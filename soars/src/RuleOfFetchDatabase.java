import jp.soars.core.*;
import java.util.*;

/**
 * 毎ティック介入があるか，データベースにチェックしいに行く
 * @author miyanishi
 * */

public class RuleOfFetchDatabase extends TAgentRule {
    /**
     * コンストラクタ
     * @param name ルール名
     * @param owner このルールをもつ役割
     */
    public RuleOfFetchDatabase(String name, TRole owner){
        super(name, owner);
    }

    @Override
    public final void doIt(TTime currentTime, Enum<?> currentStage, TSpotManager spotManager,
                           TAgentManager agentManager, Map<String, Object> globalSharedVariables) {
        Poi.fetchUpdates(spotManager);
    }
}
