flowchart TD
    %% Actors
    YoutubeLiveChatPollingActor[["YoutubeLiveChatPollingActor"]]
    ChatRoomActor[["ChatRoomActor"]]
    
    %% Services
    YoutubeLiveChatServiceTyped["YoutubeLiveChatServiceTyped"]
    PollService["PollService"]
    InferUserOptionService["InferUserOptionService"]
    ChatService["ChatService"]
    CurrencyTransferService["CurrencyTransferService"]
    UserService["UserService"]
    
    %% Repositories
    UserRepo["UserRepository"]
    YtUserRepo["YtUserRepository"]
    YtStreamerRepo["YtStreamerRepository"]
    StreamerEventRepo["StreamerEventRepository"]
    EventPollRepo["EventPollRepository"]
    PollOptionRepo["PollOptionRepository"]
    PollVoteRepo["PollVoteRepository"]
    UserStreamerStateRepo["UserStreamerStateRepository"]
    LoginInfoRepo["LoginInfoRepository"]
    OAuth2InfoRepo["OAuth2InfoRepository"]
    
    %% External dependencies
    
    
    %% YoutubeLiveChatServiceTyped dependencies
    YoutubeLiveChatServiceTyped --> YtStreamerRepo
    YoutubeLiveChatServiceTyped --> UserStreamerStateRepo
    YoutubeLiveChatServiceTyped --> UserRepo
    YoutubeLiveChatServiceTyped --> YtUserRepo
    YoutubeLiveChatServiceTyped --> PollService
    YoutubeLiveChatServiceTyped --> InferUserOptionService
    YoutubeLiveChatServiceTyped --> ChatService
    YoutubeLiveChatServiceTyped --> PollVoteRepo
    YoutubeLiveChatServiceTyped --> YoutubeLiveChatPollingActor
    
    %% YoutubeLiveChatPollingActor dependencies
    YoutubeLiveChatPollingActor --> YtStreamerRepo
    YoutubeLiveChatPollingActor --> UserStreamerStateRepo
    YoutubeLiveChatPollingActor --> UserRepo
    YoutubeLiveChatPollingActor --> YtUserRepo
    YoutubeLiveChatPollingActor --> PollService
    YoutubeLiveChatPollingActor --> InferUserOptionService
    YoutubeLiveChatPollingActor --> ChatService
    YoutubeLiveChatPollingActor --> PollVoteRepo
    
    %% ChatService dependencies
    ChatService --> ChatRoomActor
    
    %% PollService dependencies
    PollService --> EventPollRepo
    PollService --> PollOptionRepo
    PollService --> PollVoteRepo
    PollService --> UserStreamerStateRepo
    
    %% UserService dependencies
    UserService --> UserRepo
    UserService --> YtUserRepo
    UserService --> LoginInfoRepo
    UserService --> OAuth2InfoRepo
    
    %% CurrencyTransferService dependencies
    CurrencyTransferService --> UserStreamerStateRepo
    CurrencyTransferService --> YtStreamerRepo
    
    %% Repository dependencies
    YtUserRepo --> UserRepo
    EventPollRepo --> StreamerEventRepo
    PollOptionRepo --> EventPollRepo
    
    %% Styling
    classDef service fill:#c4e3f3,stroke:#6baed6
    classDef repository fill:#e5f5e0,stroke:#74c476
    classDef actor fill:#fdd0a2,stroke:#fd8d3c
    classDef external fill:#f2f2f2,stroke:#969696
    
    class YoutubeLiveChatServiceTyped,PollService,InferUserOptionService,ChatService,CurrencyTransferService,UserService service
    class UserRepo,YtUserRepo,YtStreamerRepo,StreamerEventRepo,EventPollRepo,PollOptionRepo,PollVoteRepo,UserStreamerStateRepo,LoginInfoRepo,OAuth2InfoRepo repository
    class YoutubeLiveChatPollingActor,ChatRoomActor actor
