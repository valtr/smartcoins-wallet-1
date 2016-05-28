package de.bitshares_munich.smartcoinswallet;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.StringEntity;
import de.bitshares_munich.Interfaces.AssetDelegate;
import de.bitshares_munich.Interfaces.BalancesDelegate;
import de.bitshares_munich.models.AccountDetails;
import de.bitshares_munich.models.DecodeMemo;
import de.bitshares_munich.models.TransactionDetails;
import de.bitshares_munich.utils.Application;
import de.bitshares_munich.utils.Crypt;
import de.bitshares_munich.utils.Helper;
import de.bitshares_munich.utils.IWebService;
import de.bitshares_munich.utils.ServiceGenerator;
import de.bitshares_munich.utils.TinyDB;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by Syed Muhammad Muzzammil on 5/20/16.
 */
public class TransactionActivity implements BalancesDelegate {
    Context context;
    AssetDelegate assetDelegate;
    Application application = new Application();
    int id_in_work;
    int id_total_size;
    int names_total_size;
    int names_in_work;
    int assets_id_in_work;
    int assets_id_total_size;
    int memo_in_work;
    int memo_total_size;
    int number_of_transactions_in_queue;



    HashMap<String,HashMap<String,String>> arrayof_Amount_AssetId = new HashMap<>();
    ArrayList<String> blocks;
    HashMap<String,String> timestamp;
    ArrayList<HashMap<String,String>> memos = new ArrayList<>();
    HashMap<String,String> decodememos = new HashMap<>();;
    HashMap<String,String> eRecipts = new HashMap<>();;

    BalancesDelegate balancesDelegate;
    String accountid;
    List<String> ofNames = new ArrayList<>();
    List<String> asset_ids = new ArrayList<>();
    HashMap<String,String> Names_from_Api = new HashMap<>();
    HashMap<String,HashMap<String,String>> Symbols_Precisions = new HashMap<>();
    String wifkey;
    public TransactionActivity(Context c,String account_id , AssetDelegate instance , String wif_key , int number_of_transactions_loaded){
        context = c;
        assetDelegate = instance;
        balancesDelegate = this;
        application.registerBalancesDelegate(this);
        accountid = account_id;
        try{
        wifkey = Crypt.getInstance().decrypt_string(wif_key);}
        catch (Exception e){
            testing("namak",e,"wifkey");
        };
        timestamp = new HashMap<>();
        if(account_id!=null)
        get_relative_account_history(account_id,"8",number_of_transactions_loaded);
    }
    void get_relative_account_history(final String account_id, final String id,final int n) {

        final Handler handler = new Handler();

        final Runnable updateTask = new Runnable() {
            @Override
            public void run() {
                if (Application.webSocketG != null && (Application.webSocketG.isOpen()) )
                {
                        int history_id = Helper.fetchIntSharePref(context,context.getString(R.string.sharePref_history));
                        String getDetails = "{\"id\":" + id + ",\"method\":\"call\",\"params\":["+history_id+",\"get_relative_account_history\",[\""+account_id+"\",0,25,"+n+"]]}";
                        Application.webSocketG.send(getDetails);
                }
                else {
                    get_relative_account_history(account_id,id,n);

                }
            }
        };

        handler.postDelayed(updateTask, 1000);
    }

    @Override
    public void OnUpdate(String s,int id) {
        if(id==8){
            onFirstCall(s);
        }
        if(id==9){
            if(id_in_work<id_total_size) {
                String result = returnParse(s,"result");
                String time = returnParse(result,"timestamp");
                timestamp.put(Integer.toString(id_in_work),time);
                if(id_in_work==(id_total_size-1)){
                    names_in_work=0;
                    get_names(ofNames.get(names_in_work),"10");
                }
                id_in_work++;
                if(id_in_work<blocks.size())
                get_Time(blocks.get(id_in_work),"9");
            }
        }
        if(id==10) {
            if (names_in_work < names_total_size) {
                String result = returnParse(s,"result");
                String nameObject = returnArrayObj(result,0);
                String name = returnParse(nameObject,"name");
                Names_from_Api.put(ofNames.get(names_in_work),name);
                if(names_in_work==(names_total_size-1)){
                    assets_id_in_work=0;
                    get_asset(asset_ids.get(assets_id_in_work),"11");
                }
                names_in_work++;
                if(names_in_work<ofNames.size())
                get_names(ofNames.get(names_in_work),"10");
            }
        }
        if(id==11) {
            if (assets_id_in_work < assets_id_total_size) {
               String result = returnParse(s,"result");
                String assetObject = returnArrayObj(result,0);
                String symbol = returnParse(assetObject,"symbol");
                String precision = returnParse(assetObject,"precision");
                HashMap<String,String> de = new HashMap<>();
                de.put("symbol",symbol);
                de.put("precision",precision);
                Symbols_Precisions.put(asset_ids.get(assets_id_in_work),de);
                if(assets_id_in_work==(assets_id_total_size-1)){
                    testing("namak","","testing");

                    HashMap<String,String> def = new HashMap<>();
                    memo_in_work = 0;
                    def = memos.get(memo_in_work);
                    decodeMemo(def.get("memo"),def.get("memo_id"));
               //     onLastCall();
                }
                assets_id_in_work++;
                if(assets_id_in_work<asset_ids.size()) get_asset(asset_ids.get(assets_id_in_work),"11");
            }
        }
    }
    void onFirstCall(String s) {
        String result = returnParse(s, "result");
        int totalarrays = TotalArraysOfObj(result);
        if(totalarrays!=-1) {
            for (int i = 0; i < totalarrays; i++) {
                eRecipts.put(Integer.toString(i),returnArrayObj(result,i));
            }
        }
        testing("special",eRecipts,"eRecipts");
        HashMap<String, ArrayList<String>> arrayofOP = returnParseArray(result, "op");
        HashMap<String, ArrayList<String>> arrayofblock_num = returnParseArray(result, "block_num");
        blocks = arrayofblock_num.get("block_num");
        number_of_transactions_in_queue = arrayofOP.get("op").size();
        for (int i = 0; i < arrayofOP.get("op").size(); i++) {
            String breakArray = returnArrayObj(arrayofOP.get("op").get(i), 1);
            HashMap<String, String> mapof_All = new HashMap<>();
            String parseOfamount = returnParse(breakArray, "amount");
            String from = returnParse(breakArray, "from");
            String to = returnParse(breakArray, "to");
            String amount = returnParse(parseOfamount, "amount");
            String asset_id = returnParse(parseOfamount, "asset_id");
            mapof_All.put("amount", amount);
            mapof_All.put("asset_id", asset_id);
            mapof_All.put("from", from);
            mapof_All.put("to", to);
            ofNames.add(from);
            ofNames.add(to);
            asset_ids.add(asset_id);
            if (breakArray.contains("memo")) {
                String memojson = returnParse(breakArray, "memo");
                HashMap<String,String> de = new HashMap<>();
                de.put("memo",memojson);
                de.put("memo_id",Integer.toString(i));
                memos.add(de);

            }
            arrayof_Amount_AssetId.put(Integer.toString(i), mapof_All);
        }


        HashSet<String> hashSet = new HashSet<String>();
        hashSet.addAll(ofNames);
        ofNames.clear();
        ofNames.addAll(hashSet);

        names_total_size = ofNames.size();

        assets_id_total_size = asset_ids.size();

        id_total_size = blocks.size();

        memo_total_size = memos.size();

        testing("namak",memo_in_work,"memo_total_size");
//        if(arrayof_Amount_AssetId.size()==blocks.size())
//        id_total_size = arrayof_Amount_AssetId.size();
//        else id_total_size = Math.min(arrayof_Amount_AssetId.size(),blocks.size());

        id_in_work = 0;
        get_Time(blocks.get(id_in_work),"9");
    }

//    String get_memo(String memo){
//        final String memoStr[] = new String[1];
//        String toGet = "http://188.166.147.110:9003";
//        // "{"method":"decode_memo","memo":"" + memo + "\",\"wifkey\":\"" + wifikey + "\"}";
//        testing("namak",memo,"memo");
//        testing("namak",wifkey,"wifkey");
//        String toPass =  "{\"method\":\"decode_memo\",\"memo\":\"" + memo + "\",\"wifkey\":\"" + wifkey + "\"}";
//        testing("namak",toPass,"toPass");
//        RequestParams params =  new RequestParams();
//        params.put("method","decode_memo");
//        params.put("memo",memo);
//        params.put("wifkey",wifkey);
//        params.setUseJsonStreamer(true);
//
//        AsyncHttpClient client = new AsyncHttpClient();
//        client.addHeader("Content-Type", "application/json");
//        client.post(toGet , params , new AsyncHttpResponseHandler() {
//            @Override
//            public void onStart() {
//                Log.i("euro", "start");
//            }
//
//            @Override
//            public void onSuccess(int statusCode, Header[] headers, byte[] timeline) {
//                try {
//                    byte[] bytes = timeline;
//                   // String s = new String(bytes);
//                    memoStr[0] = new String(bytes);
//                    testing("namak",bytes,"bytes");
//
//                    //   finishWithResult(s);
////                    onLastCall();
//                } catch (Exception j) {
//
//                }
//            }
//
//            @Override
//            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
//                // called when response HTTP status is "4XX" (eg. 401, 403, 404)
//                Log.i("euro", "failed");
//            }
//
//            @Override
//            public void onRetry(int retryNo) {
//                // called when request is retried
//                Log.i("euro", "retry");
//
//            }
//        });
////        testing("namak",memoStr[0],"memoStr[0]");
//        return memoStr[0];
//    }c

    private void decodeMemo(final String memo, final String key) {
        HashMap hm = new HashMap();
        hm.put("method","decode_memo");
        hm.put("wifkey",wifkey);
        hm.put("memo", memo);
        ServiceGenerator sg = new ServiceGenerator(context.getString(R.string.account_from_brainkey_url));
        IWebService service = sg.getService(IWebService.class);
        final Call<DecodeMemo> postingService = service.getDecodedMemo(hm);
        postingService.enqueue(new Callback<DecodeMemo>() {
            @Override
            public void onResponse(Response<DecodeMemo> response) {
                if (response.isSuccess()) {
                    DecodeMemo resp = response.body();
                    if (resp.status.equals("success")){
                        //tvMemo.setText(resp.msg);
                        testing("namak",resp.msg,"msg");
                        if(memo_in_work<memo_total_size) {
                            decodememos.put(key, resp.msg);
                            if(memo_in_work==(memo_total_size-1)){
                               onLastCall();
                            }
                            HashMap<String, String> def = new HashMap<>();
                            memo_in_work++;
                            if(memo_in_work<memos.size()) {
                                def = memos.get(memo_in_work);
                                decodeMemo(def.get("memo"), def.get("memo_id"));
                            }
                        }
                    }else if(resp.status.equals("failure")){
                        if(memo_in_work<memo_total_size) {
                            decodememos.put(key,"----");
                            if(memo_in_work==(memo_total_size-1)){
                                onLastCall();
                            }
                            HashMap<String, String> def = new HashMap<>();
                            memo_in_work++;
                            if(memo_in_work<memos.size()) {
                                def = memos.get(memo_in_work);
                                decodeMemo(def.get("memo"), def.get("memo_id"));
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, "No internet connection", Toast.LENGTH_SHORT).show();

                }
            }

            @Override
            public void onFailure(Throwable t) {
                testing("bijli",t,"past_break");
            }
        });
    }

    HashMap<String,ArrayList<String>> returnParseArray(String Json , String req){
        try {
            JSONArray myArray = new JSONArray(Json);
            ArrayList<String> array = new ArrayList<>();
            HashMap<String, ArrayList<String>> pairs = new HashMap<String, ArrayList<String>>();
            for (int i = 0; i < myArray.length(); i++) {
                JSONObject j = myArray.optJSONObject(i);
                Iterator it = j.keys();
                while (it.hasNext()) {
                    String n = (String) it.next();
                    if (n.equals(req)) {
                        array.add(j.getString(n));
                        pairs.put(req, array);
                    }
                }

            }
            return pairs;
        }catch (Exception e){

        }
        return null;
    }
    String returnParse(String Json , String req){
        try {
            if(Json.contains(req)){
                JSONObject myJson = new JSONObject(Json);
                return  myJson.getString(req);}
        }catch (Exception e){}
        return "";
    }
    String returnArrayObj(String Json , int position){
        try {
            JSONArray myArray = new JSONArray(Json);
            if(myArray.length()>=position){
                return  myArray.get(position).toString();
            }
        }catch (Exception e){}
        return "";
    }
    int TotalArraysOfObj(String Json){
        try {
            JSONArray myArray = new JSONArray(Json);
            return myArray.length();
        }catch (Exception e){}
        return -1;
    }
    void get_Time(String block_num,String id){
        int db_id = Helper.fetchIntSharePref(context,context.getString(R.string.sharePref_database));
      //  {"id":4,"method":"call","params":[2,"get_block_header",[6356159]]}
        String getDetails = "{\"id\":" + id + ",\"method\":\"call\",\"params\":[" + db_id + ",\"get_block_header\",[ " + block_num + "]]}";
        Application.webSocketG.send(getDetails);
    }
    void get_names(String name_id,String id){
        int db_id = Helper.fetchIntSharePref(context,context.getString(R.string.sharePref_database));
        //    {"id":4,"method":"call","params":[2,"get_accounts",[["1.2.101520"]]]}
        String getDetails = "{\"id\":" + id + ",\"method\":\"call\",\"params\":[" + db_id + ",\"get_accounts\",[[\"" + name_id + "\"]]]}";
        Application.webSocketG.send(getDetails);
    }
    void get_asset(String asset, String id) {
        //{"id":1,"method":"get_assets","params":[["1.3.0","1.3.120"]]}
        String getDetails = "{\"id\":" + id + ",\"method\":\"get_assets\",\"params\":[[\""+asset+"\"]]}";
        Application.webSocketG.send(getDetails);
    }
    void testing(String msg , Object obj , String nameOfObject){
        Log.i("Saiyed_Testing","=> Msg : "+ msg + " : nameOfObject : " + nameOfObject + " : " + obj);
    }
    void testing(String msg , Exception e , String nameOfObject){
        StackTraceElement[] stackTrace = e.getStackTrace();
        String fullClassName = stackTrace[stackTrace.length - 1].getClassName();
        String className = fullClassName.substring(fullClassName
                .lastIndexOf(".") + 1);
        String methodName = stackTrace[stackTrace.length - 1].getMethodName();
        int lineNumber = stackTrace[stackTrace.length - 1].getLineNumber();
        Log.i("Saiyed_Testing","=> Msg : "+ msg + " : nameOfObject : " + nameOfObject + " : " + fullClassName + "--" + className + "--" + methodName + "--" + lineNumber);
    }
    void onLastCall(){
        List<TransactionDetails> transactionDetails = new ArrayList<>();
        for(int i = 0 ; i < arrayof_Amount_AssetId.size() ; i++) {
            try {
                HashMap<String, String> mapof_All = new HashMap<>();
                mapof_All = arrayof_Amount_AssetId.get(Integer.toString(i));
                String Date = timestamp.get(Integer.toString(i));
                Boolean Sent = false;
                String fromid = mapof_All.get("from");
                if (fromid.equals(accountid)) Sent = true;
                String from = Names_from_Api.get(fromid);
                String toid = mapof_All.get("to");
                String to = Names_from_Api.get(toid);
                String memo = "----";
                String eRecipt = "";
                if (decodememos.containsKey(Integer.toString(i))) {
                    memo = decodememos.get(Integer.toString(i));
                }
                if (eRecipts.containsKey(Integer.toString(i))) {
                    eRecipt = eRecipts.get(Integer.toString(i));
                }
              //  String memo = mapof_All.get("message");
                String assetid = mapof_All.get("asset_id");
                HashMap<String, String> sym_pre = Symbols_Precisions.get(assetid);
                String amount = mapof_All.get("amount");
                String amount_pre = returnFromPower(sym_pre.get("precision"), amount);
                String symbol = sym_pre.get("symbol");
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                Date formatted = null;
                formatted = formatter.parse(Date);
                TransactionDetails object = new TransactionDetails(formatted , Sent, to, from, memo, Float.parseFloat(amount_pre), symbol, 0f, "" ,eRecipt,context);
                transactionDetails.add(object);
            }catch(Exception e){
                testing("error" , e , "Try,Catch");
            }
        }
        assetDelegate.TransactionUpdate(transactionDetails,number_of_transactions_in_queue);
    }
    String returnFromPower(String i,String str){
        Double ok = 1.0;
        Double pre = Double.valueOf(i);
        Double value = Double.valueOf(str);
        for(int k = 0 ; k<pre ; k++ ){
            ok = ok*10;
        }
        return  Double.toString(value/ok);
    }
}
