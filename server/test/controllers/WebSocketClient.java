package controllers;

import play.shaded.ahc.org.asynchttpclient.AsyncHttpClient;
import play.shaded.ahc.org.asynchttpclient.BoundRequestBuilder;
import play.shaded.ahc.org.asynchttpclient.ListenableFuture;
import play.shaded.ahc.org.asynchttpclient.netty.ws.NettyWebSocket;
import play.shaded.ahc.org.asynchttpclient.ws.WebSocket;
import play.shaded.ahc.org.asynchttpclient.ws.WebSocketListener;
import play.shaded.ahc.org.asynchttpclient.ws.WebSocketUpgradeHandler;

import java.util.concurrent.CompletableFuture;

public class WebSocketClient {

  private AsyncHttpClient client;

  public WebSocketClient(AsyncHttpClient c) {
    this.client = c;
  }

  public CompletableFuture<NettyWebSocket> call(String url, String origin, WebSocketListener listener) {
    final BoundRequestBuilder requestBuilder = client.prepareGet(url).addHeader("Origin", origin);
    System.out.println(requestBuilder);
    System.out.println(origin);
    final WebSocketUpgradeHandler handler = new WebSocketUpgradeHandler.Builder().addWebSocketListener(listener).build();
    ListenableFuture<NettyWebSocket> future = requestBuilder.execute(handler);
    return future.toCompletableFuture();
  }

  static class LoggingListener implements WebSocketListener {

    private Throwable throwableFound = null;

    public Throwable getThrowable() {
      return throwableFound;
    }

    @Override
    public void onOpen(WebSocket websocket) {
      // do nothing
      System.out.println("Web socket open");
      System.out.println(websocket);
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s) {
      // do nothing
      System.out.println("Web socket close");
      System.out.println(webSocket);
    }

    public void onError(Throwable t) {
      //logger.error("onError: ", t);
      System.out.println("Web socket error");
      System.out.println(t.getMessage());
      throwableFound = t;
    }
  }

}