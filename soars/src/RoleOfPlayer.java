import jp.soars.core.TAgent;
import jp.soars.core.TRole;

/**
 * 介入者役割
 * @author miyanishi
 */

public class RoleOfPlayer extends TRole {
    /** データベースから差分がないかチェックするルール */
    public static final String RULE_NAME_OF_FETCH_DATABASE = "FetchDatabase";

    public RoleOfPlayer(TAgent owner){
        super(RoleName.Player, owner);
        new RuleOfFetchDatabase(RULE_NAME_OF_FETCH_DATABASE, this).setStage(Stage.FetchIntervention);
    }

}
