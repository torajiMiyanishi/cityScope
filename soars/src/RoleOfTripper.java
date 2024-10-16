import jp.soars.core.*;
import jp.soars.modules.gis_otp.otp.TOtpResult;
import jp.soars.modules.gis_otp.otp.TOtpResult.EOtpStatus;
import jp.soars.modules.gis_otp.role.*;
import jp.soars.modules.gis_otp.otp.TOtpState;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;

import java.util.*;

/**
 * Tripper役割．
 * Person役割を参考に，自宅とpoiを同列に扱う移動を実行する役割
 * 出発時刻制約のみ考慮する
 *
 * poiのジャンルを入力値とし，目的地の決定から実際の移動まで行う
 */
public class RoleOfTripper extends TRole {

    /** 出発ルール */
    private TRuleOfLeavingOrigin ruleOfLeavingOrigin;
    /** 到着ルール */
    private TRuleOfArrivingAtDestination ruleOfArrivingAtDestination;
    /** 移動行動が終了したら，自身をディアクティベートする */
    public static final String RULE_NAME_OF_DEACTIVATE = "Deactivate";

    /** 移動情報を保持 */
    public static Map<TTime,Double[]> inTripLocations = new HashMap<>();

    /** 起点 */
    private TSpot fOriginSpot;
    /** 優先順位付けされた終点poiの候補地 */
    private List<TSpot> fPrioritizedPois;
    /** 現在時刻 */
    private TTime fCurrentTime;


    /**
     * コンストラクタ
     * @param owner
     * @param time
     */
    public RoleOfTripper(TAgent owner, TTime time){
        super(RoleName.Tripper, owner);
        fCurrentTime = time;
    }


    /**
     * 旅行計画
     */
    public void planning(TSpot originSpot, List<TSpot> candidatePois) {
        // 旅行者役割にodをセット
        fOriginSpot = originSpot;
        fPrioritizedPois = prioritizeCandidates(candidatePois); // poiを評価して優先順位付け

        TRoleOfGisAgent rga = (TRoleOfGisAgent) getOwner().getRole(ERoleName.GisAgent);
        //        TraverseModeSet traverseModeSet = determineModeSet(rga); //移動手段を決定する．
        TraverseModeSet traverseModeSet = determineModeSet(); //CAR固定でテスト

        /**
         * 優先順位順に，pathが張れるかチェックし，張れたらそこへ移動．
         * 張れなければ，時点の候補地をチェック．以降繰り返し
         * */
        int hour = fCurrentTime.getHour(); //家を出発する時
        int minute = fCurrentTime.getMinute(); //家を出発する分
        boolean arriveBy = false; //出発時刻で検索
        TTripInformation ti = null;
        TSpot determinedDestination = null;
        for (TSpot poiSpot : fPrioritizedPois) {
            ti = rga.findRoute(hour, minute, arriveBy, traverseModeSet, ((TAgent) getOwner()).getCurrentSpot(), poiSpot); //経路検索
            if (ti != null) { //経路が見つかったら
                if (ti.getSearchStatus() == EOtpStatus.SUCCESS) {// tripが成功するならば
                    determinedDestination = poiSpot; // ログ用に決定した目的地を保存
                    break;
                }
            }
        }
        RuleOfDeactivate deactivateRule = new RuleOfDeactivate(RULE_NAME_OF_DEACTIVATE, this);
        if (ti != null && ti.getSearchStatus() == EOtpStatus.SUCCESS && determinedDestination != null) { // 経路が存在し，tripとして成立しているの出れば
            rga.scheduleToMove(ti,fCurrentTime); // 移動をスケジュール
            getOwner().activateRole(RoleName.Tripper); // 旅行者役割を活性化
            TOtpResult currentOtpResult = rga.findRoutes(1, traverseModeSet, arriveBy, hour, minute, ti.getOrigin(), ti.getDestination()); // ログ用にTOtpResultを再取得
            TTime inTripTime = new TTime(fCurrentTime.getDay() + ti.getStartDay(), ti.getStartHour(), ti.getStartMinute(), 0);

            while (inTripTime.isLessThan(new TTime(fCurrentTime.getDay() + ti.getEndDay(), ti.getEndHour(), ti.getEndMinute(), 0))) {

                try {
                    TOtpState otpState = currentOtpResult.getState(0, inTripTime.getHour(), inTripTime.getMinute());
                    inTripLocations.put(new TTime(inTripTime), new Double[]{otpState.getLatitude(), otpState.getLongitude()});
                } catch (NullPointerException n) {
                    System.err.println("GetState return null @RoleOfTripper");
                }
                inTripTime.add(0, 1, 0); // 1step分ずつ増やす
            }

            // 決定した移動行動の予約とtick内移動への対応
            if (fCurrentTime.isEqualTo(new TTime(fCurrentTime.getDay() + ti.getEndDay(), ti.getEndHour(), ti.getEndMinute(), 0))){ // 出発時刻と到達時刻が同じティック内の場合
                deactivateRule.setTimeAndStage(fCurrentTime.getDay() + ti.getEndDay(), ti.getEndHour(), ti.getEndMinute(), 0, Stage.DeactivateWithCancel);//旅行計画が正常にスケジュールされた場合は，trip終了時刻に不活性化
            } else {
                deactivateRule.setTimeAndStage(fCurrentTime.getDay() + ti.getEndDay(), ti.getEndHour(), ti.getEndMinute(), 0, Stage.Deactivate);//旅行計画が正常にスケジュールされた場合は，trip終了時刻に不活性化
            }
            // 移動先が介入対象ならば，移動先に通知
            if (SpotType.Poi == determinedDestination.getType()){
                RoleOfPoi poiRole = (RoleOfPoi)determinedDestination.getRole(RoleName.Poi);
                poiRole.addArrivingAgent((TAgent)getOwner());
            }

        } else {
            if (ti == null) {
                System.err.println("Failed to find any route. @RoleOfTripper");
            } else if (ti.getSearchStatus() != EOtpStatus.SUCCESS) {
                System.err.println("Status of trip information is NOT SUCCESS. @RoleOfTripper");
            } else {
                System.err.println("DeterminedDestination is NULL @RoleOfTripper");
            }
            deactivateRule.setTimeAndStage(fCurrentTime.getDay(), fCurrentTime.getHour(), fCurrentTime.getMinute(), 0, Stage.DeactivateWithCancel);//旅行計画が失敗した場合，即刻不活性化．
            /**
             * 今後，どこにも移動できなくなってしまう状況に陥ることが発生しうる場合，対処法をここに記述
             */
        }
    }

    /**
     * TripperRoleが一つのTripが終了したとき実行するメソッド
     * 1.TripperRoleをディアクティベートする
     * 2.位置情報のログを初期化する。（安全のため、2時間を超過した記録のみ削除）
     */
    public void endTripExecution(){
        // ディアクティベート
        getOwner().deactivateRole(RoleName.Tripper);
        // 位置情報記録のセーフティな削除
        if (fCurrentTime.isGreaterThan(new TTime(0,2,1,0))){ // シミュレーション開始時刻よりも前を参照しないためのガードコード
            Iterator<Map.Entry<TTime, Double[]>> iterator = inTripLocations.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<TTime, Double[]> entry = iterator.next();
                TTime keyTime = entry.getKey();
                if (keyTime.isLessThan(fCurrentTime.getDay(), fCurrentTime.getHour() - 2, fCurrentTime.getMinute(), 0)) { // 二時間以上前のものは
                    iterator.remove(); // 安全に削除
                }
            }
        }
    }

    /**
     * 目的地の優先順位をつけるメソッド。
     * 現状ではランダムに並び替えるが、将来的に評価値やハフモデルなどを実装予定。
     *
     * @param candidateSpots 候補となるスポットのリスト
     * @return ランダムに並び替えられたスポットリスト
     */
    public List<TSpot> prioritizeCandidates(List<TSpot> candidateSpots) {
        // 候補リストをコピーする
        List<TSpot> prioritizedSpots = new ArrayList<>(candidateSpots);

        // ランダムに並び替える
        getRandom().shuffle(prioritizedSpots);

        return prioritizedSpots;
    }


    /**
     * テスト用
     * 強制的に自動車で移動させる
     * */
    private TraverseModeSet determineModeSet(){
        return new TraverseModeSet(TraverseMode.CAR);
    }


    /**
     * 現在時刻の
     * @return latlon 緯度経度の配列
     */
    public Double[] getCurrentLatLon(){
        // locationLog用に 成功したルートでTOtpResultを取りに行く
        Double[] latlon = inTripLocations.get(fCurrentTime);
        return latlon;
    }

}

