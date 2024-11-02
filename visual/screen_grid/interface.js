// 四角の範囲のサイズを取得
const squareElement = document.querySelector('.center-square');
const squareWidth = squareElement.offsetWidth;
const squareHeight = squareElement.offsetHeight;

async function updateCircles() {
    try {
        // `fetch_positions`から位置データを取得
        const response = await fetch('http://localhost:3001/fetch_positions');
        const positions = await response.json();

        // 既存の円を削除
        document.querySelectorAll('.data-circle').forEach(circle => circle.remove());

        // 新しい位置データをもとに円を配置
        positions.forEach(position => {
            const { x_rel, y_rel } = position;

            // 新しい円要素を作成
            const circle = document.createElement('div');
            circle.classList.add('data-circle');

            // x_relとy_relを四角の範囲内に正規化して位置を設定
            circle.style.left = `${(x_rel / 1000) * squareWidth}px`; // 1000は基準サイズ
            circle.style.top = `${(y_rel / 1000) * squareHeight}px`;
            squareElement.appendChild(circle);
        });
    } catch (error) {
        console.error("位置データの取得に失敗しました:", error);
    }
}
// 0.1秒（100ms）ごとにupdateCirclesを実行
setInterval(updateCircles, 100);

async function updateAcc() {
    try {
        // 地図の範囲を取得
        const responseMapBounds = await fetch('http://localhost:3001/fetch_map_bounds');
        const dataMapBounds = await responseMapBounds.json();

        // POIのデータを取得
        const responsePoi = await fetch('http://localhost:3001/fetch_poi');
        const dataPoi = await responsePoi.json();

        // 必要なデータが取得できたかをチェック
        if (!dataMapBounds || !dataPoi) {
            console.error("必要なデータが取得できませんでした");
            return;
        }

        const { top_left_lat, top_left_lng, bottom_left_lat, top_right_lng } = dataMapBounds;

        // 画面上のすべての円の位置を取得
        for (const circle of document.querySelectorAll('.data-circle')) {
            const rect = circle.getBoundingClientRect();
            const x = rect.left;
            const y = rect.top;

            // ブラウザの相対座標から地理座標に変換
            const lat = top_left_lat + (y / window.innerHeight) * (bottom_left_lat - top_left_lat);
            const lng = top_left_lng + (x / window.innerWidth) * (top_right_lng - top_left_lng);

            // /update_block_latlon に POST リクエストを送信
            await fetch('http://localhost:3001/update_block_latlon', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    lat: lat,
                    lon: lng
                })
            });


            // 各 POI と円の位置の距離を計算し、アクセシビリティを更新
            for (const poi of dataPoi) {
                const distance = calculateDistance(lat, lng, poi.lat, poi.lon);

                // 距離に基づいてアクセシビリティを計算（例：距離が小さいほど高い値）


                // アクセシビリティを更新
                await fetch('http://localhost:3001/update_acc', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        uuid: poi.uuid,
                        accessibility: distance
                    })
                });
            }
        }
    } catch (error) {
        console.error("アクセシビリティの更新に失敗しました:", error);
    }
}

// 緯度経度の距離計算関数
function calculateDistance(lat1, lng1, lat2, lng2) {
    const toRadians = (degrees) => (degrees * Math.PI) / 180;
    const R = 6371e3; // 地球の半径 (メートル)

    const φ1 = toRadians(lat1);
    const φ2 = toRadians(lat2);
    const Δφ = toRadians(lat2 - lat1);
    const Δλ = toRadians(lng2 - lng1);

    const a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
        Math.cos(φ1) * Math.cos(φ2) *
        Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c; // メートルで返す
}


setInterval(updateAcc, 1000);

