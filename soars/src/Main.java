import java.io.*;
import java.util.*;
import java.sql.*;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import jp.soars.core.TAgent;
import jp.soars.core.TAgentManager;
import jp.soars.core.TRuleExecutor;
import jp.soars.core.TSOARSBuilder;
import jp.soars.core.TSpot;
import jp.soars.core.TSpotManager;
import jp.soars.core.TTime;
import jp.soars.core.enums.ERuleDebugMode;

import jp.soars.modules.gis_otp.logger.TPersonTripLogger;
import jp.soars.modules.gis_otp.otp.TThreadLocalOfOtpRouter;
import jp.soars.modules.gis_otp.role.EStage;
import jp.soars.modules.gis_otp.role.TRoleOfGisSpot;
import jp.soars.modules.gis_otp.role.TRoleOfGisAgent;
import jp.soars.modules.gis_otp.role.TSpotOnTheWayMaker;


//import jp.soars.modules.synthetic_population.TRoleOfSyntheticPopulationData;
//import jp.soars.modules.synthetic_population.TSyntheticPopulationData;
//import jp.soars.modules.synthetic_population.TSyntheticPopulationData.THousehold;
//import jp.soars.modules.synthetic_population.ERoleName;

import jp.soars.utils.random.ICRandom;

import org.opentripplanner.common.geometry.SphericalDistanceLibrary;

/**
 * メインクラス
 * @author miyanishi
 */
public class Main {


    public static void main(String[] args) throws Exception {
        // *************************************************************************************************************
        // TSOARSBuilderの必須設定項目
        //   - simulationStart:シミュレーション開始時刻
        //   - simulationEnd:シミュレーション終了時刻
        //   - tick:1ステップの時間間隔
        //   - stages:使用するステージリスト(実行順)
        //   - agentTypes:使用するエージェントタイプ集合
        //   - spotTypes:使用するスポットタイプ集合
        // *************************************************************************************************************
        long startTime = System.currentTimeMillis(); //実験開始時刻
        String simulationStart  = "0/00:00:00";
        String simulationEnd    = "1/0:00:00";
        String tick                = "0:01:00";
        String behaviorTick        = "0:15:00"; // 行為決定のティック
        long seed = 10400L; // マスターシード値
        int noOfPeople = 100;
        List<Enum<?>> stages = List.of(Stage.FetchIntervention, EStage.AgentArriving, Stage.Deactivate, Stage.DecideBehavior, EStage.AgentPlanning, EStage.AgentDeparting, Stage.DeactivateWithCancel);
        Set<Enum<?>> layers = new HashSet<>();
        Collections.addAll(layers, Layer.values());
        Layer defaultLayer = Layer.Geospatial;
//        TSOARSBuilder builder = new TSOARSBuilder(simulationStart, simulationEnd, tick, stages, agentTypes, spotTypes);
        TSOARSBuilder builder = createBuilder(simulationStart, simulationEnd, tick, stages, seed, layers, defaultLayer); //SOARSビルダ
        // *************************************************************************************************************
        // TSOARSBuilderの任意設定項目
        // *************************************************************************************************************

        // 行為更新ステージを毎時刻ルールが実行される定期実行ステージとして登録
        builder.setPeriodicallyExecutedStage(Stage.DecideBehavior, simulationStart, behaviorTick);
        builder.setPeriodicallyExecutedStage(Stage.FetchIntervention, simulationStart, tick);

        // warningの表示設定
//        builder.setWarningFlag(false);

//        // マスター乱数発生器のシード値設定
//        long seed = 0L;
//        builder.setRandomSeed(seed);

        // gisデータ類のパス

//        String dirToInput = "C:\\Users\\tora2\\IdeaProjects\\LifeBehavior\\";
        String dirToInput = "C:\\Users\\tora2\\IdeaProjects\\cityScope\\data\\";
        String pathOfPopulationDataFile = dirToInput + "pop\\spdata_"+noOfPeople+".csv"; //合成人口データファイル
        String pathToPoiCsv = dirToInput + "poi\\zenrin_building.csv"; //poiのcsv
        String pathToPoiSql = dirToInput + "database\\yokosuka_test.db"; // poiのdatabase
        String pathToPbf = dirToInput + "trans\\14100-road.osm.pbf"; //OpenStreetMap用のPBFファイル
        String dirToGtfs = dirToInput + "trans"; //石垣市のGTFSファイルが格納されているディレクトリ

        // ルールログとランタイムログの出力設定
        String pathOfLogCDir = "C:\\Users\\tora2\\IdeaProjects\\cityScope\\logs\\";
//        String pathOfLogZDir = "Z:\\lab\\output\\logs\\";
        String fileNameHead = "seed_"+ seed + "_no_" + noOfPeople + "_";
        builder.setRuleLoggingEnabled(pathOfLogCDir + File.separator + fileNameHead + "rule_log.csv");
        builder.setRuntimeLoggingEnabled(pathOfLogCDir + File.separator + fileNameHead + "runtime_log.csv");
        String personTripLog = fileNameHead + "person_trips"; //移動ログ
        String spotLog = fileNameHead + "spot_log.csv"; //スポットログ
        String behaviorLog = fileNameHead + "behavior_log.csv"; // 行為ログ
        String locationLog = fileNameHead + "location_log.csv"; // 位置情報（緯度経度）のログ

        // ルールログのデバッグ情報出力設定
        builder.setRuleDebugMode(ERuleDebugMode.LOCAL);

        // *************************************************************************************************************
        // TSOARSBuilderでシミュレーションに必要なインスタンスの作成と取得
        // *************************************************************************************************************

        builder.build();
        TRuleExecutor ruleExecutor = builder.getRuleExecutor();
        TAgentManager agentManager = builder.getAgentManager();
        TSpotManager spotManager = builder.getSpotManager();
        ICRandom random = builder.getRandom();
        Map<String, Object> globalSharedVariableSet = builder.getGlobalSharedVariableSet();


        // *************************************************************************************************************
        // モデル実行に必要な各クラスを初期化
        // *************************************************************************************************************
        // 行為確率を管理するクラス
        Behavior.initialize();
        // poiを管理するクラス
        List<TSpot> generatedPois = Poi.initializePoiLocation(spotManager, Layer.Geospatial, pathToPoiSql, pathToPoiCsv);

        // *************************************************************************************************************
        // エージェントとスポットを生成
        // *************************************************************************************************************

        //合成人口データの読み込み
        List<SyntheticPopulationData> spDatas = SyntheticPopulationData.raedDataFromCsvFile(pathOfPopulationDataFile);
        // household_idに基づいて自宅生成
        HashMap<String,TSpot> householdIdToHome = new HashMap<>();
        for (SyntheticPopulationData spData: spDatas){
            String householdId = spData.getHouseholdId();
            if (!householdIdToHome.containsKey(householdId)){
                TSpot home = spotManager.createSpots(SpotType.Home,1,Layer.Geospatial).get(0);
                householdIdToHome.put(householdId,home); // マスタに追加
                new TRoleOfGisSpot(home, spData.getLatitude(), spData.getLongitude(), String.valueOf(spData.getMeshcode()));
            }
        }
        // poi spotの作成
        // >>>> Poiクラスに移行

        // 途中スポットの作成
        TSpotOnTheWayMaker.create(spotManager, spotManager.getSpots(SpotType.Home), SpotType.SpotOnTheWay); //自宅スポットの途中スポット

        // testのためのスポット
        TSpot testSpot = spotManager.createSpots(SpotType.Test,1,Layer.Test).get(0);

        //エージェントの生成
        int noOfPersons = spDatas.size(); // 合成人口データで定義された人数
        List<TAgent> persons = agentManager.createAgents(AgentType.Person, noOfPersons); // Personエージェントを生成
        for (int i = 0; i < persons.size(); ++i) {
            TAgent person = persons.get(i); // i番目のエージェントを取り出す．
            if (i == 0){
                new RoleOfPlayer(person);
                person.activateRole(RoleName.Player);
            }
            SyntheticPopulationData spData = spDatas.get(i); //i番目のエージェントの人工合成データ
            new TRoleOfGisAgent(person,UUID.randomUUID().toString()); //GISエージェント役割を生成してエージェントに割り当てる
            String householdId = spData.getHouseholdId(); //世帯ID
            TSpot home = householdIdToHome.get(householdId); // 自宅を取得
            new RoleOfResident(person,home,spData); // 住民役割
            person.activateRole(RoleName.Resident);
            person.initializeCurrentSpot(home); //初期位置を自宅スポットに設定する．
            person.initializeCurrentSpot(testSpot); // テスト用にtestレイヤのスポットを設定．
            new RoleOfTripper(person,ruleExecutor.getCurrentTime()); // 動的な移動計画
            person.activateRole(jp.soars.modules.gis_otp.role.ERoleName.GisAgent); //GISエージェント役割をアクティブ化

            /** gis-otp-moduleとマルコフ連鎖生活行動モデルの結合 */
            new RoleOfBehavior(person, spData.getAge(), spData.getSexId()); // 行為者役割を作成
            person.activateRole(RoleName.Behavior); // 行為者役割をアクティブ化
        }

        // *************************************************************************************************************
        // 独自に作成するログ用のPrintWriter
        //   - スポットログ:各時刻での各エージェントの現在位置ログ
        // *************************************************************************************************************
        // print writerを作り，カラムを出力
        PrintWriter behaviorLogPW = new PrintWriter(pathOfLogCDir + File.separator + behaviorLog);// 行為ログ用PrintWriter
        writePivotColumns(behaviorLogPW,persons);
        PrintWriter spotLogPW = new PrintWriter(pathOfLogCDir + File.separator + spotLog); // スポットログ
        writePivotColumns(spotLogPW,persons);
        PrintWriter locationPW = new PrintWriter(pathOfLogCDir + File.separator + locationLog); // 位置情報ログ
        writeLocationLogColumns(locationPW);


        // git-otp-moduleの設定
        TPersonTripLogger.open(pathOfLogCDir, personTripLog); //移動ログをオープン
        TThreadLocalOfOtpRouter.initialize(pathToPbf, dirToGtfs); //OTPルータの初期化
        // *************************************************************************************************************
        // シミュレーションのメインループ
        // *************************************************************************************************************

        // 1ステップ分のルールを実行 (ruleExecutor.executeStage()で1ステージ毎に実行することもできる)
        // 実行された場合:true，実行されなかった(終了時刻)場合は:falseが帰ってくるため，while文で回すことができる．
        while (ruleExecutor.executeStep()) {
            /** 標準出力 */
            System.out.print(ruleExecutor.getCurrentTime());
            System.out.println();
            /** ログの出力 */
            //スポットログの出力
            writeSpotLog(spotLogPW, ruleExecutor.getCurrentTime(), persons);
            // 行為ログ出力
            writeBehaviorLog(behaviorLogPW,ruleExecutor.getCurrentTime(),persons);
            // 位置情報ログ
//            if (ruleExecutor.getCurrentTime().getMinute() == 0){ // 位置情報は一時間に一度書き出す．
//                writeLocationLog(locationPW,ruleExecutor.getCurrentTime(),persons);
//            }
            writeLocationLog(locationPW,ruleExecutor.getCurrentTime(),persons);

        }

        // *************************************************************************************************************
        // シミュレーションの終了処理
        // *************************************************************************************************************

        ruleExecutor.shutdown();

        behaviorLogPW.close();

        TPersonTripLogger.close(); //移動ログをクローズ
        spotLogPW.close(); // スポットログをクローズ
        System.out.println("Elapsed time: " + (System.currentTimeMillis() - startTime) / 1000 + "[sec]"); //実行時間
    }


    /**
     * SOARSビルダを生成する．
     * @param startTime シミュレーション開始時刻
     * @param endTime シミュレーション終了時刻
     * @param tick １ステップの時間間隔
     * @param stages ステージリスト
     * @param seed マスター乱数シード
     * @return SOARSビルダ
     * @throws IOException
     */
    private static TSOARSBuilder createBuilder(String startTime, String endTime,
                                               String tick, List<Enum<?>> stages, long seed, Set<Enum<?>> layers, Enum<Layer> defaultLayer) throws IOException {
        Set<Enum<?>> agentTypes = new HashSet<>(); // 全エージェントタイプ
        Set<Enum<?>> spotTypes = new HashSet<>(); // 全スポットタイプ
        Collections.addAll(agentTypes, AgentType.values()); // EAgentType に登録されているエージェントタイプをすべて追加
        Collections.addAll(spotTypes, SpotType.values()); // ESpotType に登録されているスポットタイプをすべて追加
        TSOARSBuilder builder = new TSOARSBuilder(startTime, endTime, tick, stages, agentTypes, spotTypes,layers,defaultLayer); // ビルダー作成
        builder.setRandomSeed(seed); // シード値設定
        // builder.setRuleLoggingEnabled(pathOfLogDir + File.separator + "rule_log.csv") // ルールログ出力設定
        //        .setRuntimeLoggingEnabled(pathOfLogDir + File.separator + "runtime_log.csv"); // ランタイムログ出力設定
        builder.setRuleDebugMode(ERuleDebugMode.LOCAL); // ローカル設定に従う
        builder.build(); // インスタンスのビルド
        return builder;
    }



    /**
     * スポットログを出力する．
     * @param pw 出力ストリーム
     * @param currentTime 現在時刻
     * @param persons エージェントのリスト
     */
    private static void writeSpotLog(PrintWriter pw, TTime currentTime, List<TAgent> persons) {
        pw.print(currentTime); // 現在時刻
        pw.print(","+Day.getDay(currentTime.getDay()));
        for (TAgent person : persons) {
            pw.print("," + person.getCurrentSpotName());
        }
        pw.println();
        pw.flush();
    }
    /**
     * 行為ログを出力する
     * @param pw 出力ストリーム
     * @param currentTime 現在時刻
     * @param persons エージェントのリスト
     */
    private static void writeBehaviorLog(PrintWriter pw, TTime currentTime, List<TAgent> persons) {
        pw.print(currentTime); // 現在時刻
        pw.print(","+Day.getDay(currentTime.getDay()));
        for (TAgent person : persons) {
            RoleOfBehavior behaviorRole = (RoleOfBehavior) person.getRole(RoleName.Behavior);
            pw.print("," + behaviorRole.getCurrentBehavior());
        }
        pw.println();
        pw.flush();
    }

    /**
     * エージェント分の横持ちカラムを記述する
     * @param logPW
     * @param persons
     */
    private static void writePivotColumns(PrintWriter logPW, List<TAgent> persons){
        logPW.print("CurrentTime,CurrentDay");
        for (TAgent person : persons) {
            logPW.print(',');
            logPW.print(person.getName());
        }
        logPW.println();
    }

    /**
     * 緯度経度ログのカラム定義
     * @param pw print writer
     * @throws FileNotFoundException
     */
    private static void writeLocationLogColumns(PrintWriter pw) throws FileNotFoundException {
        // スポットログのヘッダのカラム名出力
        pw.print("PersonId,Gender,Age,Day,CurrentTime,NttTime,Latitude,Longitude");
        pw.println();
    }

    /**
     * 緯度経度ログの出力
     * @param pw 出力ストリーム
     * @param currentTime 現在時刻
     * @param persons エージェントのリスト
     */
    private static void writeLocationLog(PrintWriter pw, TTime currentTime, List<TAgent> persons) {
        // ※参考 カラム >> PersonId,Gender,Age,Day,CurrentTime,NttTime,Latitude,Longitude
        // 時刻を変換
        String HHMM = Day.formatTime(currentTime.getHour(),currentTime.getMinute());
        Day.DayType day = Day.getDay(currentTime.getDay());


        for (TAgent person : persons) { // 各エージェントから残りの情報を取得
            // LatLon
            Double[] latlon = new Double[2];
            TSpot currentSpot = person.getCurrentSpot();
            if (currentSpot.getType() == SpotType.SpotOnTheWay){ // 途中スポットの場合
                RoleOfTripper tripperRole = (RoleOfTripper) person.getRole(RoleName.Tripper);
                latlon = tripperRole.getCurrentLatLon();

                if (latlon == null || latlon[0] == null || latlon[1] == null) {
                    System.err.println("Cannot find current Latitude, Longitude FROM SpotOnTheWay @Main");
//                    System.exit(1);
                }
            } else if (currentSpot.getType() == SpotType.Home || currentSpot.getType() == SpotType.Poi) { // 地理空間上のスポットの場合
                TRoleOfGisSpot gisSpotRole = (TRoleOfGisSpot) currentSpot.getRole(jp.soars.modules.gis_otp.role.ERoleName.GisSpot);
                latlon = new Double[] {gisSpotRole.getLatitude(),gisSpotRole.getLongitude()};
            }
            if (latlon == null || latlon[0] == null || latlon[1] == null) {
                System.out.println(currentSpot);
                System.err.println("Cannot find current Latitude, Longitude @Main");
//                System.exit(1);
            } else {
                // PersonId
                pw.print(person.getName());
                // Gender
                RoleOfResident residentRole = (RoleOfResident) person.getRole(RoleName.Resident);
                Behavior.Gender gender = (residentRole.getSyntheticPopulationData().getSexId().equals(0)) ? Behavior.Gender.MALE: Behavior.Gender.FEMALE;
                pw.print("," + gender);
                // Age
                pw.print("," + residentRole.getSyntheticPopulationData().getAge());
                // Day
                pw.print("," + day);
                // CurrentTime
                pw.print("," + currentTime);
                // NttTime
                pw.print("," + HHMM);
                // LatLon
                pw.print("," + latlon[0] + "," + latlon[1]);

                pw.println();
            }
        }
        pw.flush();
    }
}