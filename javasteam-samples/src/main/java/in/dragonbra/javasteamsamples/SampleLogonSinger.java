package in.dragonbra.javasteamsamples;

import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.networking.steam3.ProtocolTypes;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver;
import in.dragonbra.javasteam.steam.discovery.ServerRecord;
import in.dragonbra.javasteam.steam.handlers.steamapps.SteamApps;
import in.dragonbra.javasteam.steam.handlers.steamapps.callback.AppOwnershipTicketCallback;
import in.dragonbra.javasteam.steam.handlers.steamapps.callback.GetClientAppListResponseCallback;
import in.dragonbra.javasteam.steam.handlers.steamapps.callback.VACStatusCallback;
import in.dragonbra.javasteam.steam.handlers.steamfriends.SteamFriends;
import in.dragonbra.javasteam.steam.handlers.steamfriends.callback.FriendAddedCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.MachineAuthDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.OTPDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.SteamUser;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.UpdateMachineAuthCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.WebAPIUserNonceCallback;
import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager;
import in.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration;
import in.dragonbra.javasteam.types.JobID;
import in.dragonbra.javasteam.types.SteamID;
import in.dragonbra.javasteam.util.log.DefaultLogListener;
import in.dragonbra.javasteam.util.log.LogManager;

/**
 * @author lngtr
 * @since 2018-02-23
 */
@SuppressWarnings("Duplicates")
public class SampleLogonSinger implements Runnable {

    private SteamClient steamClient;

    private CallbackManager manager;

    private SteamUser steamUser;

    private SteamApps steamApps;

    private SteamFriends steamFriends;

    private boolean isRunning;

    private String user;

    private String pass;

    public SampleLogonSinger(String user, String pass) {
        this.user = user;
        this.pass = pass;
    }

    public static void main(String[] args) throws Exception {
        SteamID steamID=new SteamID(76561199018825992L);
        System.out.println(steamID.getAccountID());

        LogManager.addListener(new DefaultLogListener());
//        new SampleLogon("zztest2", "12345678_zz").run();

//        ThreadPoolUtil.async(new SampleLogonSinger("parmlf3017", "gf2A3L8Ye2Jl"));
//        ThreadPoolUtil.async(new SampleLogonSinger("nbshq2", "steam123ZAQ"));
        ThreadPoolUtil.async(new SampleLogonSinger("nbshq7", "steam123ZAQ"));
    }

    @Override
    public void run() {

        // create our steamclient instance
        SteamConfiguration steamConfiguration=SteamConfiguration.create(iSteamConfigurationBuilder -> {
            iSteamConfigurationBuilder.withProtocolTypes(ProtocolTypes.ALL);
//            Proxy proxy=new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10809));
//            OkHttpClient okHttpClient=new OkHttpClient.Builder().proxy(proxy).build();
//            iSteamConfigurationBuilder.withHttpClient(okHttpClient);
        });
        steamClient = new SteamClient(steamConfiguration);

        // create the callback manager which will route callbacks to function calls
        manager = new CallbackManager(steamClient);

        // get the steamuser handler, which is used for logging on after successfully connecting
        steamUser = steamClient.getHandler(SteamUser.class);
        steamApps = steamClient.getHandler(SteamApps.class);
        steamFriends = steamClient.getHandler(SteamFriends.class);
        // register a few callbacks we're interested in
        // these are registered upon creation to a callback manager, which will then route the callbacks
        // to the functions specified
        manager.subscribe(ConnectedCallback.class, this::onConnected);
        manager.subscribe(DisconnectedCallback.class, this::onDisconnected);

        manager.subscribe(LoggedOnCallback.class, this::onLoggedOn);
        manager.subscribe(LoggedOffCallback.class, this::onLoggedOff);

        manager.subscribe(VACStatusCallback.class,this::onVACStatus);
        manager.subscribe(GetClientAppListResponseCallback.class,this::onAppListResponse);

        manager.subscribe(AppOwnershipTicketCallback.class,this::onAppOwnershipTicketCallback);
        manager.subscribe(WebAPIUserNonceCallback.class,this::onWebAPIUserNonceCallback);
        manager.subscribe(FriendAddedCallback.class,this::onFriendAddedCallback);

        manager.subscribe(UpdateMachineAuthCallback.class,this::onUpdateMachineAuthCallback);




        isRunning = true;

        System.out.println("Connecting to steam...");

        // initiate the connection
//        steamClient.connect();
        steamClient.connect(ServerRecord.createWebSocketServer("cm3-ct-sha2.cm.wmsjsteam.com:27020"));

        // create our callback handling loop
        while (isRunning) {
            // in order for the callbacks to get routed, they need to be handled by the manager
            manager.runWaitCallbacks(1000L);
        }
        System.out.println(user+",run finshed,steamId:"+steamClient.getSteamID().getAccountID());
    }

    private void onWebAPIUserNonceCallback(WebAPIUserNonceCallback callback){
        System.out.println(callback.getNonce());
        System.out.println(callback);
    }

    private void onAppOwnershipTicketCallback(AppOwnershipTicketCallback callback){
        System.out.println(callback.getTicket());
        System.out.println(callback);

        steamFriends.addFriend(new SteamID(76561199018825992L));
    }

    private void onFriendAddedCallback(FriendAddedCallback friendAddedCallback){

        System.out.println(friendAddedCallback);
        System.out.println(friendAddedCallback);
    }

    private void onVACStatus(VACStatusCallback callback){
        if (callback.getBannedApps().size()>0) {
            System.err.println(user+" has VAC: "+callback.getBannedApps());
        }else{
            System.err.println(user+" has`t VAC");
        }
        steamApps.getClientAppList();
        steamApps.getAppOwnershipTicket(1097150);
        steamUser.requestWebAPIUserNonce();

//        steamUser.activeSteamGuard();
    }

    private void onUpdateMachineAuthCallback(UpdateMachineAuthCallback updateMachineAuthCallback){

        OTPDetails otp = new OTPDetails();
        otp.setIdentifier(updateMachineAuthCallback.getOneTimePassword().getIdentifier());
        otp.setType(updateMachineAuthCallback.getOneTimePassword().getType());
        otp.setValue(41);

        MachineAuthDetails details = new MachineAuthDetails();

        details.setJobID(updateMachineAuthCallback.getJobID());
        details.setFileName(updateMachineAuthCallback.getFileName());
        details.setBytesWritten(updateMachineAuthCallback.getBytesToWrite());
        details.setFileSize(16);
        details.setOffset(updateMachineAuthCallback.getOffset());
        details.seteResult(EResult.OK);
        details.setLastError(1);
        details.setOneTimePassword(otp);
        details.setSentryFileHash(updateMachineAuthCallback.getData());

        steamUser.sendMachineAuthResponse(details);
    }

    private void onConnected(ConnectedCallback callback) {
        System.out.println("Connected to Steam! Logging in " + user + "...");

        LogOnDetails details = new LogOnDetails();
        details.setUsername(user);
        details.setPassword(pass);
        details.setLauncherType(1);

        steamUser.logOn(details);
    }

    private void onDisconnected(DisconnectedCallback callback) {
        System.out.println("Disconnected from Steam,acc:"+user+", isUserInitiated:"+callback.isUserInitiated());

        isRunning = false;
    }

    private void onLoggedOn(LoggedOnCallback callback) {
        if (callback.getResult() != EResult.OK) {
            if (callback.getResult() == EResult.AccountLogonDenied) {
                // if we recieve AccountLogonDenied or one of it's flavors (AccountLogonDeniedNoMailSent, etc)
                // then the account we're logging into is SteamGuard protected
                // see sample 5 for how SteamGuard can be handled
                System.out.println("Unable to logon to Steam: This account is SteamGuard protected.");
                isRunning = false;
                return;
            }else if (callback.getResult() == EResult.InvalidPassword) {

            }else if (callback.getResult() == EResult.RateLimitExceeded) {
                System.err.println(" WARN to change IP!!!!!!!!!!!!!!!");
                System.err.println(" WARN to change IP");
                System.err.println(" WARN to change IP!!!!!!!!!!!!!!!");
            }else if (callback.getResult() == EResult.RateLimitExceeded) {

            }else if (callback.getResult() == EResult.AccountLoginDeniedNeedTwoFactor) {

            }

            System.out.println("Unable to logon to Steam: " + callback.getResult());
            isRunning = false;
            return;

        }
        System.out.println("Successfully logged on!");

        steamApps.getClientAppList();
        steamApps.getAppOwnershipTicket(872410);
    }

    private void onLoggedOff(LoggedOffCallback callback) {
        System.out.println("Logged off of Steam: " + callback.getResult());
        isRunning = false;
    }

    private void onAppListResponse(GetClientAppListResponseCallback callback){
        for (SteammessagesClientserver.CMsgClientGetClientAppListResponse.App app : callback.getAppsList()) {
            for (SteammessagesClientserver.CMsgClientGetClientAppListResponse.App.DLC dlc : app.getDlcsList()) {
                System.out.println("dlc:::::::"+dlc);
            }
        }
    }
}
