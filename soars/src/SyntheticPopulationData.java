import jp.soars.modules.synthetic_population.TSyntheticPopulationData;
import org.geotools.xml.xsi.XSISimpleTypes;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;


public class SyntheticPopulationData {
    // フィールド変数
    private int age;
    private String sexId;
    private String roleHouseholdTypeId;
    private double latitude;
    private double longitude;
    private String employmentTypeId;
    private String meshcode;
    private String industryTypeId;
    private String householdId;

    // コンストラクタ
    public SyntheticPopulationData(int age, String sexId, String roleHouseholdTypeId, String employmentTypeId,
                                   String industryTypeId, double latitude, double longitude, String householdId,String meshcode) {
        this.age = age;
        this.sexId = sexId;
        this.roleHouseholdTypeId = roleHouseholdTypeId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.employmentTypeId = employmentTypeId;
        this.householdId = householdId;
        this.meshcode = meshcode;
        this.industryTypeId = industryTypeId;
    }

    // ゲッターとセッター
    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getSexId() {
        return sexId;
    }

    public void setSexId(String sexId) {
        this.sexId = sexId;
    }

    public String getRoleHouseholdTypeId() {
        return roleHouseholdTypeId;
    }

    public void setRoleHouseholdTypeId(String roleHouseholdTypeId) {
        this.roleHouseholdTypeId = roleHouseholdTypeId;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getEmploymentTypeId() {
        return employmentTypeId;
    }

    public void setEmploymentTypeId(String employmentTypeId) {
        this.employmentTypeId = employmentTypeId;
    }

    public String getMeshcode() {
        return meshcode;
    }

    public void setMeshcode(String meshcode) {
        this.meshcode = meshcode;
    }

    public String getIndustryTypeId() {
        return industryTypeId;
    }

    public void setIndustryTypeId(String industryTypeId) {
        this.industryTypeId = industryTypeId;
    }

    public String getHouseholdId() {
        return householdId;
    }

    public void setHouholdId(String householdId) {
        this.householdId = householdId;
    }

    // オブジェクトの詳細を出力するメソッド
    @Override
    public String toString() {
        return "SyntheticPopulationData{" +
                "age=" + age +
                ", sexId=" + sexId +
                ", roleHouseholdTypeId=" + roleHouseholdTypeId +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", employmentTypeId=" + employmentTypeId +
                ", meshcode=" + meshcode +
                ", industryTypeId=" + industryTypeId +
                '}';
    }

    /**
     * csvから直接データを生成する
     * @param path
     * @return
     * @throws IOException
     */
    public static List<SyntheticPopulationData> raedDataFromCsvFile(String path) throws IOException{
        ArrayList<SyntheticPopulationData> result = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(path));

        // 1行目（ヘッダー）をスキップ
        br.readLine();

        for (String line = br.readLine(); line != null; line = br.readLine()) {
            String[] record = line.split(",");
            SyntheticPopulationData spdata = new SyntheticPopulationData(
                    Integer.parseInt(record[0]),
                    record[1],
                    record[2],
                    record[3],
                    record[4],
                    Double.parseDouble(record[5]),
                    Double.parseDouble(record[6]),
                    record[7],
                    record[8]
                    );
            result.add(spdata);
        }
        return result;
    }

    /**
     * ユニークなhouseholdIdを作成する
     * @param spdatas
     * @return
     */
    public static HashSet<String> getHouseholdIdSet(List<SyntheticPopulationData> spdatas){
        HashSet<String> result = new HashSet<>();
        for (SyntheticPopulationData spdata: spdatas){
            result.add(spdata.getHouseholdId());
        }
        return result;
    }

}

