package in.dragonbra.javasteam.steam.handlers.steamapps.callback;

import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientCheckAppBetaPasswordResponse;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg;
import in.dragonbra.javasteam.types.JobID;
import in.dragonbra.javasteam.util.Strings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This callback is received when a ClientAppListResponse
 */
public class GetClientAppListResponseCallback extends CallbackMsg {

    private List<SteammessagesClientserver.CMsgClientGetClientAppListResponse.App> appsList;

    public GetClientAppListResponseCallback(JobID jobID, SteammessagesClientserver.CMsgClientGetClientAppListResponse.Builder msg) {
        setJobID(jobID);
        this.appsList = msg.getAppsList();
    }

    public List<SteammessagesClientserver.CMsgClientGetClientAppListResponse.App> getAppsList() {
        return appsList;
    }
}
