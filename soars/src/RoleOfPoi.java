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
    Behavior.PoiData poiData;

    /**
     * コンストラクタ
     * @param owner この役割を持つエージェント
     * @param poiData poiの情報
     * */
    public RoleOfPoi(TSpot owner, Behavior.PoiData poiData, double latitude, double longitude, String meshcode){
        // 親クラスのコンストラクタを呼び出す．
        // 以下の2つの引数は省略可能で，その場合デフォルト値で設定される．
        // 第3引数:この役割が持つルール数 (デフォルト値 10)
        // 第4引数:この役割が持つ子役割数 (デフォルト値 5)
        super(RoleName.Poi, owner, 0, 0);
        new TRoleOfGisSpot(owner, latitude, longitude, meshcode);
        this.poiData = poiData;
        MapApp.update(owner); // 自身のpoi情報をマスタに格納する
    }
    // Getter
    public Behavior.PoiData getPoiData(){
        return poiData;
    }
}
