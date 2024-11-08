import java.sql.*;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import jp.soars.core.*;
import jp.soars.modules.gis_otp.role.ERoleName;
import jp.soars.modules.gis_otp.role.TRoleOfGisSpot;
import jp.soars.modules.gis_otp.role.TSpotOnTheWayMaker;

public class Poi {

    /** データベースへのパス */
    public static String fPathToDatabase;

    /** 生成予定のpoiを格納 */
    public static HashMap<String, Behavior.PoiData> fToBeGenerate = new HashMap<>();

    /**
     * poiの情報が格納されているcsvを読み込み，シミュレーションのpoiの状態を管理するデータベースを作成する
     * 加えて同時にpoi spotを作成する
     * @param spotManager spotManager
     * @param layer layer
     * @param pathToDataBase SQLiteのデータベースファイルのパス
     * @param pathToPoiCsv POI情報が格納されているCSVファイルのパス
     */
    public static List<TSpot> initializePoiLocation(TSpotManager spotManager, Layer layer, String pathToDataBase, String pathToPoiCsv) {
        // データベースへのパスを格納
        fPathToDatabase = pathToDataBase;

        // 返り値用にリストを定義
        List<TSpot> generatedPois = new ArrayList<>();

        // 既存のテーブルを削除するSQL文
        String dropTableSQL = "DROP TABLE IF EXISTS poi_location";

        // 新しいテーブルを作成するSQL文
        String createTableSQL = "CREATE TABLE poi_location ("
                + "poi_id TEXT PRIMARY KEY, "
                + "genre TEXT, "
                + "area REAL, "
                + "latitude REAL, "
                + "longitude REAL, "
                + "address TEXT, "
                + "mesh_code TEXT, "
                + "industry_type TEXT, "
                + "behavior_type TEXT, "
                + "abs_attract_score INTEGER"
                + ");";

        String insertPoiIntervenedSQL = "INSERT INTO poi_location (poi_id, genre, area, latitude, longitude, address, mesh_code, industry_type, behavior_type, abs_attract_score) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            // SQLite JDBCドライバをロード
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        // SQLiteデータベースに接続
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + pathToDataBase)) {
            // 既存のテーブルを削除
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(dropTableSQL);
                stmt.execute(createTableSQL);
            }

            // CSVファイルをOpenCSVで読み込み
            try (CSVReader reader = new CSVReader(new FileReader(pathToPoiCsv));
                 PreparedStatement pstmtPoi = conn.prepareStatement(insertPoiIntervenedSQL)) {

                String[] values;
                reader.readNext();  // ヘッダーをスキップ
                while ((values = reader.readNext()) != null) {
                    // CSVの各行からデータを取得
                    String poiId = UUID.randomUUID().toString();  // POIごとにUUIDを生成
                    String genre = values[0];
                    double area = Double.parseDouble(values[1]);
                    double latitude = Double.parseDouble(values[2]);
                    double longitude = Double.parseDouble(values[3]);
                    String address = values[4];
                    String gridCode = values[5];
                    String industryType = values[6];
                    String behaviorType = values[7];
                    int absAttractScore = Integer.parseInt(values[8]);

                    // spot作成
                    TSpot poiSpot = spotManager.createSpot(SpotType.Poi, poiId, layer);
                    Behavior.PoiData poiData = new Behavior.PoiData(genre, address, industryType, behaviorType, area, absAttractScore, latitude, longitude, gridCode);
                    new RoleOfPoi(poiSpot, poiData); // poi役割
                    TSpotOnTheWayMaker.create(spotManager, poiSpot, SpotType.SpotOnTheWay); // 途中スポットの作成

                    generatedPois.add(poiSpot); // 返り値用に格納

                    // データベースに挿入
                    pstmtPoi.setString(1, poiId);
                    pstmtPoi.setString(2, genre);
                    pstmtPoi.setDouble(3, area);
                    pstmtPoi.setDouble(4, latitude);
                    pstmtPoi.setDouble(5, longitude);
                    pstmtPoi.setString(6, address);
                    pstmtPoi.setString(7, gridCode);
                    pstmtPoi.setString(8, industryType);
                    pstmtPoi.setString(9, behaviorType);
                    pstmtPoi.setInt(10, absAttractScore);

                    pstmtPoi.executeUpdate();
                }
            } catch (CsvValidationException e) {
                e.printStackTrace();
            }

        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }

        return generatedPois;
    }



    /**
     * データベースに更新データを取りに行く
     */
    public static void fetchUpdates(TSpotManager spotManager) {
        // query: read_progressから最新のtime_stampを取得
        String getLastFetchTimestampSQL = "SELECT time_stamp FROM read_progress";
        String getPoiIntervenedUpdatesSQL = "SELECT * FROM poi_intervened";

        // read_progressテーブルにレコードが存在するか確認するSQL文
        String checkRecordExistsSQL = "SELECT COUNT(*) FROM read_progress";
        String insertOrUpdateReadProgressSQL;

        // SQLite JDBCドライバをロード
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return;
        }

        // SQLiteデータベースに接続
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + fPathToDatabase)) {
            // 最新のtime_stampをread_progressから取得
            long lastTimeStampFromReadProgress = 0;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(getLastFetchTimestampSQL)) {

                if (rs.next()) {
                    lastTimeStampFromReadProgress = rs.getLong("time_stamp");
                }
            }

            // もしtime_stampが取得できた場合にpoi_intervenedのデータを取得
            if (lastTimeStampFromReadProgress != 0) {
                getPoiIntervenedUpdatesSQL += " WHERE time_stamp > ?"; // 最新取得時刻が存在したらWHERE句を追加
            }
            try (PreparedStatement pstmt = conn.prepareStatement(getPoiIntervenedUpdatesSQL)) {
                // もしtime_stampが取得できた場合にpoi_intervenedのデータを取得
                if (lastTimeStampFromReadProgress != 0) {
                    pstmt.setLong(1, lastTimeStampFromReadProgress); // プレースホルダにtime_stampをセット
                }

                long lastTimeStampToReadProgress = lastTimeStampFromReadProgress;
                try (ResultSet rs = pstmt.executeQuery()) {
                    boolean needRegenerate = false;
                    while (rs.next()) {
                        // poi_intervenedの各列の値を取得
                        long time_stamp = rs.getLong("time_stamp");
                        String poiId = rs.getString("poi_id");
                        String genre = rs.getString("genre");
                        double area = rs.getDouble("area");
                        double latitude = rs.getDouble("latitude");
                        double longitude = rs.getDouble("longitude");
                        String address = rs.getString("address");
                        String gridCode = rs.getString("mesh_code");
                        String industryType = rs.getString("industry_type");
                        String behaviorType = rs.getString("behavior_type");
                        int absAttractScore = rs.getInt("abs_attract_score");
                        boolean isGenerated = rs.getInt("is_generated") == 1;


                        if (isGenerated) { // 生成された場合
                            fToBeGenerate.put(poiId, new Behavior.PoiData(genre, address, industryType, behaviorType, area, absAttractScore, latitude, longitude, gridCode)); // 生成対象を記憶させる
                        } else { // 削除された場合
                            if (spotManager.getSpotDB().get(poiId) != null) { // 重複リクエストでないか確認
                                TSpot deletingSpot = spotManager.getSpotDB().get(poiId);
                                RoleOfPoi poiRole = (RoleOfPoi) deletingSpot.getRole(RoleName.Poi);
                                poiRole.setToBeDeleted(true); // 削除対象フラグを立てる
                                needRegenerate = true;
                            }
                        }
                        // soars側の認知をカメラモジュールに伝達する
                        if (time_stamp > lastTimeStampToReadProgress) {
                            lastTimeStampToReadProgress = time_stamp;
                        } else {
                            System.err.println("time_stamp: " + time_stamp + "lastTimeStampFromReadProgress" + lastTimeStampFromReadProgress + "lastTimeStampToReadProgress" + lastTimeStampToReadProgress + " @Poi.java");
                            System.exit(1);
                        }
                    }

                    applyIntervention(spotManager); // 介入を反映

                    // 削除されるpoiがある場合，MapAppのマスタ類を再構成する
                    if (needRegenerate){
                        MapApp.regenerate(spotManager);
                    }



                }

                if (lastTimeStampFromReadProgress != lastTimeStampToReadProgress) {
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(checkRecordExistsSQL)) {
                        rs.next();
                        boolean recordExists = rs.getInt(1) > 0;

                        if (recordExists) {
                            // レコードが存在する場合は更新
                            insertOrUpdateReadProgressSQL = "UPDATE read_progress SET time_stamp = ?";
                        } else {
                            // レコードが存在しない場合は挿入
                            insertOrUpdateReadProgressSQL = "INSERT INTO read_progress (time_stamp) VALUES (?)";
                        }

                        try (PreparedStatement pstmtRp = conn.prepareStatement(insertOrUpdateReadProgressSQL)) {
                            pstmtRp.setLong(1, lastTimeStampToReadProgress);
                            pstmtRp.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * agentの所在を考慮したdeleteSpotとpoi移動の介入に対応したspotの追加削除のコントロール
     * @param spotManager
     */
    public static void applyIntervention(TSpotManager spotManager){
        // 削除対象の取得
        Set<String> deleteSpots = new HashSet<>();
        for (TSpot poi:spotManager.getSpots(SpotType.Poi)){
            RoleOfPoi poiRole = (RoleOfPoi)poi.getRole(RoleName.Poi);
            if (poiRole.toBeDeleted()){ // 削除予定である
                deleteSpots.add(poi.getName());
            }
        }

        // ベン図のように分離して，それぞれに処理を適用する．
        Set<String> onlyInDeleting = new HashSet<>();
        Set<String> inBoth = new HashSet<>();
        Set<String> onlyInGenerating = new HashSet<>();

        for (String poiId: deleteSpots) {
            if (fToBeGenerate.containsKey(poiId)){
                inBoth.add(poiId);
            } else {
                onlyInDeleting.add(poiId);
            }
        }
        for (String poiId: fToBeGenerate.keySet()){
            if (!deleteSpots.contains(poiId)){
                onlyInGenerating.add(poiId);
            }
        }

        // 生成のみ
        for (String poiId: onlyInGenerating){
            if (spotManager.getSpotDB().get(poiId) == null) { // 重複してリクエストされた際のエラーを回避する
                Behavior.PoiData poiData = fToBeGenerate.get(poiId);
                TSpot generatedSpot = spotManager.createSpot(SpotType.Poi, poiId, Layer.Geospatial);
                new RoleOfPoi(generatedSpot, poiData);
                TSpotOnTheWayMaker.create(spotManager, generatedSpot, SpotType.SpotOnTheWay); // 途中スポットの作成
            }
            fToBeGenerate.remove(poiId); // 生成予定リストから削除
        }
        // 削除のみ
        for (String poiId: onlyInDeleting){
            TSpot poiSpot = spotManager.getSpotDB().get(poiId);
            RoleOfPoi poiRole = (RoleOfPoi) poiSpot.getRole(RoleName.Poi);

            if (poiSpot.getNoOfAgents() == 0 && poiRole.getNoOfArrivingAgents() == 0){ // 滞在エージェントと到着予定エージェントが両方0の時
                delete(spotManager,poiSpot); // 途中スポットを削除
                spotManager.deleteSpot(poiSpot); // poiのスポットを削除
            }
        }
        // 削除＋生成（すなわち移動）
        for (String poiId: inBoth){
            TSpot poiSpot = spotManager.getSpotDB().get(poiId);
            RoleOfPoi poiRole = (RoleOfPoi) poiSpot.getRole(RoleName.Poi);

            if (poiSpot.getNoOfAgents() == 0 && poiRole.getNoOfArrivingAgents() == 0) { // 滞在エージェントと到着予定エージェントが両方0の時
                poiRole.moveSelf(fToBeGenerate.get(poiId)); // poi自身を移動させる
                MapApp.update(poiSpot); // mapappに再登録

                fToBeGenerate.remove(poiId); // 生成予定リストから削除
            }

        }
    }



    /**
     * 目的地スポットに付随した途中スポットの削除
     * @param spotManager
     * @param destinationSpot
     * @return
     */
    public static boolean delete(TSpotManager spotManager, TSpot destinationSpot) {
        TRoleOfGisSpot roleOfGisSpot = (TRoleOfGisSpot)destinationSpot.getRole(ERoleName.GisSpot);
        if (roleOfGisSpot == null){
            return false;
        }
        TSpot spotOnTheWay = roleOfGisSpot.getSpotOnTheWay();
        if (spotOnTheWay == null){
            return false;
        }
        spotManager.deleteSpot(spotOnTheWay);
        return true;
    }
}
