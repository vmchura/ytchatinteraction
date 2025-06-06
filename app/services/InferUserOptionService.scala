package services


import play.api.libs.ws.WSClient

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import edu.stanford.nlp.pipeline.*
import edu.stanford.nlp.ling.*
import edu.stanford.nlp.util.*
import models.{EventPoll, PollOption}

import scala.jdk.CollectionConverters.*
import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.domain.*
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionServiceFactory
import play.api.libs.json
import play.api.libs.json.Json

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.util.Try

@Singleton
class InferUserOptionService @Inject()(ws: WSClient)(implicit ec: ExecutionContext, materializer: Materializer) {
  val service = OpenAIChatCompletionServiceFactory(
    coreUrl = "http://localhost:11434/v1/"
  )
  def fromLLM(eventPoll: EventPoll, options: List[PollOption], response: String): Future[Option[(PollOption, Int)]] = {
    val optionMessages = options.zipWithIndex.map{case (po, i) => s"|Option ${i+1}: ${po.optionText}"}.mkString("\n")
    val messages = Seq(
      SystemMessage("Extract the most likely chosen option and the amount of coins or currency bet. in JSON format \n" +
        "Consider misspelling and short answers. If the option chose is found correctly: {'option': Option_chose, 'currency': Integer}\n" +
        "If the option or currency could not be determined: {'error': 'No message detected'}"),

      UserMessage(
        s"""Poll Question: ${eventPoll.pollQuestion}
          $optionMessages
          |""".stripMargin),
      UserMessage(response)
    )
    service.createChatCompletion(messages, CreateChatCompletionSettings(model = "qwen3:1.7b")).map { _.choices.headOption.map(choiceInfo => choiceInfo.message.content)
    }.map{ _.flatMap { responseString =>
        val indexThink = responseString.indexOf("</think>")
        Option.when(indexThink >=0){
          val jsonStringResponse = responseString.substring(indexThink + 9).trim
          println(jsonStringResponse)
          Try {
            val jsonResponse = Json.parse(jsonStringResponse)
            for{
              option <- (jsonResponse \ "option").asOpt[String]
              currency <- (jsonResponse \ "currency").asOpt[Int]
            }yield{
              options.find(_.optionText.toLowerCase.equals(option.toLowerCase)).map{ optionChose =>
                (optionChose, currency)
              }
            }
          }.toOption.flatten.flatten
        }.flatten

      }
    }
  }
  def inferencePollResponse(eventPoll: EventPoll, options: List[PollOption], response: String): Future[Option[(PollOption, Int)]] = {
    fromLLM(eventPoll, options, response).recover{
      case e =>
        println(s"Error with LLM: ${e.getMessage}")
        inferPollResponseWithNLP(eventPoll.pollQuestion.toLowerCase, options.map(_.optionText.toLowerCase), response.toLowerCase).flatMap{
        case (responseOption, confidence) => options.find(_.optionText.toLowerCase.equals(responseOption)).map(po => (po, confidence))
      }
    }
  }

  /**
   * Infers the chosen option and confidence value from a natural language response to a poll
   *
   * @param question The poll question
   * @param options  The list of available options
   * @param response The user's natural language response
   * @return Option containing a tuple of (matched option, confidence value) or None if inference failed
   */
  private def inferPollResponseWithNLP(
                                question: String,
                                options: List[String],
                                response: String
                              ): Option[(String, Int)] = {

    // Initialize Stanford CoreNLP pipeline
    val props = new java.util.Properties()
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner")
    props.setProperty("tokenize.language", "es")
    props.setProperty("ner.applyNumericClassifiers", "true")
    props.setProperty("ner.useSUTime", "false")
    val pipeline = new StanfordCoreNLP(props)

    // Annotate the text
    val annotation = new edu.stanford.nlp.pipeline.Annotation(normalizeText(response).mkString(" "))
    pipeline.annotate(annotation)

    // Extract sentences
    val sentences = annotation.get(classOf[CoreAnnotations.SentencesAnnotation]).asScala.toList

    // Prepare normalized versions of options for better matching
    val normalizedOptions = options.map(opt => (opt, normalizeText(opt)))

    // Find the matched option using NLP features
    val matchedOption = findBestMatchingOption(normalizedOptions, sentences)

    // Extract confidence value using NLP
    val confidence = extractConfidenceValue(sentences).flatMap(c => Option.when(c > 0)(c))

    // Return the results if we found both parts
    (matchedOption, confidence) match {
      case (Some(option), Some(conf)) => Some((option, conf))
      case _ => None
    }
  }

  /**
   * Normalizes text by converting to lowercase and removing stop words
   */
  private  def normalizeText(text: String): Set[String] = {
    val a = text.toLowerCase

    val k = (a.zip(a.tail + " ").map { case (current, next) => if ((current.isDigit && !next.isDigit) ||
      (!current.isDigit && next.isDigit)) current.toString + " " else current.toString
    }).foldLeft("")(_ + _)

    val r = k.toLowerCase
      .split("\\W+")
      .toSet

    //println(s"from $text to ${r.mkString("|")}")
    r
  }

  /**
   * Finds the best matching option using NLP features
   */
  private  def findBestMatchingOption(
                              normalizedOptions: List[(String, Set[String])],
                              sentences: List[CoreMap]
                            ): Option[String] = {

    // Extract all tokens from the response
    val tokens = sentences.flatMap(sentence =>
      sentence.get(classOf[CoreAnnotations.TokensAnnotation]).asScala
    )

    // Get lemmas (base forms) of all words, which helps with matching variations
    val lemmas = tokens.map(token =>
      token.get(classOf[CoreAnnotations.LemmaAnnotation]).toLowerCase
    ).toSet

    // Get all content words (nouns, verbs, adjectives, etc.)
    val contentWords = tokens.filter(token => {
      val pos = token.get(classOf[CoreAnnotations.PartOfSpeechAnnotation])
      pos.startsWith("NN") || pos.startsWith("VB") || pos.startsWith("JJ")
    }).map(token =>
      token.get(classOf[CoreAnnotations.LemmaAnnotation]).toLowerCase
    ).toSet

    // Score each option based on word overlap
    val scored = normalizedOptions.map { case (original, normalized) =>
      val contentWordMatches = normalized.intersect(contentWords).size
      val lemmaMatches = normalized.intersect(lemmas).size

      // Weight content words more heavily
      val score = (contentWordMatches * 2) + lemmaMatches
      (original, score)
    }

    // Return the option with the highest score, if any option matches
    val bestMatch = scored.maxByOption(_._2)
    bestMatch match {
      case Some((option, score)) if score > 0 => Some(option)
      case _ => None
    }
  }

  /**
   * Extracts a confidence value from the response using NLP features
   */
  private  def extractConfidenceValue(sentences: List[CoreMap]): Option[Int] = {


    // If no percentages found, look for cardinal numbers
    val numbers = sentences.flatMap(sentence =>
      sentence.get(classOf[CoreAnnotations.TokensAnnotation]).asScala.filter(token =>
        token.get(classOf[CoreAnnotations.NamedEntityTagAnnotation]) == "NUMBER"
      )
    )

    if (numbers.nonEmpty) {
      // Try to convert to integer
      try {
        val numText = numbers.head.get(classOf[CoreAnnotations.TextAnnotation])

        // Handle common text representations of numbers
        numText.toLowerCase match {
          case "uno" => Some(1)
          case "dos" => Some(2)
          case "tres" => Some(3)
          case "cuatro" => Some(4)
          case "cinco" => Some(5)
          // Add more as needed
          case _ => Some(numText.toInt)
        }
      } catch {
        case _: NumberFormatException => None
      }
    } else {
      // Fallback to regex pattern matching
      val numberPattern = "\\b(\\d+)\\b".r

      val all_sentences = sentences.map(_.toString)
      val allText = all_sentences.mkString(" ")
      val allWords = allText.split("\\W+").toSet
      numberPattern.findFirstIn(allText).map(_.toInt) match {
        case Some(x) => Some(x)
        case None => {
          if (allWords.contains("all") || allWords.contains("todos") || allWords.contains("todas"))
            Some(Int.MaxValue)
          else None
        }
      }
    }
  }
}