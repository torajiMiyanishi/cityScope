import jp.soars.core.TAgent;
import jp.soars.core.TRole;
import jp.soars.core.TSpot;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import jp.soars.modules.gis_otp.role.TRoleOfGisSpot;


import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;


/**
 * Poi役割
 * @author miyanishi
 */


public final class RoleOfPoi extends TRole{

    /** poiのデータ */
    private Behavior.PoiData poiData;
    /** 到着予定エージェント */
    private Set<TAgent> arrivingAgents = new HashSet<>();
    /** 削除対象フラグ */
    private boolean toBeDeleted;

    /**
     * コンストラクタ
     * @param owner この役割を持つエージェント
     * @param poiData poiの情報
     * */
    public RoleOfPoi(TSpot owner, Behavior.PoiData poiData){
        // 親クラスのコンストラクタを呼び出す．
        // 以下の2つの引数は省略可能で，その場合デフォルト値で設定される．
        // 第3引数:この役割が持つルール数 (デフォルト値 10)
        // 第4引数:この役割が持つ子役割数 (デフォルト値 5)
        super(RoleName.Poi, owner, 0, 0);
        new TRoleOfGisSpot(owner, poiData.getLatitude(), poiData.getLongitude(), poiData.getGridCode());
        this.poiData = poiData;
        this.toBeDeleted = false;
        MapApp.update(owner); // 自身のpoi情報をマスタに格納する
    }
    // Getter
    public Behavior.PoiData getPoiData(){
        return this.poiData;
    }
    public int getNoOfArrivingAgents() { return this.arrivingAgents.size(); }
    public boolean toBeDeleted() { return this.toBeDeleted; }

    // Setter
    public void addArrivingAgent(TAgent agent){
        arrivingAgents.add(agent);
    }
    public void setToBeDeleted(boolean toBeDeleted) {this.toBeDeleted = toBeDeleted;}


    /**
     * poi自身の地理空間の位置情報を移動させる
     * @param poiData
     */
    public void moveSelf(Behavior.PoiData poiData){
        this.poiData = poiData;
        this.arrivingAgents = new HashSet<>();
        this.toBeDeleted = false;
    }
}
