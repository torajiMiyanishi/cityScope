/**
 * ステージ定義
 * @author miyanishi
 */
public enum Stage {
    /** 介入の有無をチェックする */
    FetchIntervention,
    /** 状態変更ステージ */
    Deactivate,
    DeactivateWithCancel,
    /** 行為決定ステージ */
    DecideBehavior,
}
