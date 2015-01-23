package app.arch.p2pclient;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import de.javawi.jstun.attribute.MappedAddress;
import de.javawi.jstun.util.UtilityException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.pixmob.httpclient.HttpClient;
import org.pixmob.httpclient.HttpClientException;
import org.pixmob.httpclient.HttpResponse;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements AdapterView.OnItemClickListener,
                                                      AdapterView.OnItemLongClickListener,
                                                      StunClient.StunClientListener {
    public static final String TAG = "MainActivity";
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mDeviceListView = (ListView)findViewById(R.id.device_list);
        mDeviceListView.setOnItemClickListener(this);
        mDeviceListView.setOnItemLongClickListener(this);

        StunClient.getInstance().setStunClientListener(this);
        new GetMappedAddressTask().execute();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        StunClient.getInstance().close();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Device device = (Device)parent.getAdapter().getItem(position);
        new ConnectToRemoteDeviceTask().execute(device);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        Device device = (Device)parent.getAdapter().getItem(position);
        new GetDeviceListTask().execute(mDevice);
        return true;
    }

    @Override
    public void onMappedAddressReceived(MappedAddress mappedAddress) {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        try {
            Device device = new Device(androidId, mappedAddress.getAddress().getInetAddress().getHostAddress(), mappedAddress.getPort());
            new RegisterDeviceTask().execute(device);
        } catch (UtilityException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRemoteDeviceRequestReceived(MappedAddress mappedAddress) {

    }

    private class GetMappedAddressTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            StunClient.getInstance().requestMappedAddress();
            return null;
        }
    }

    private class RegisterDeviceTask extends AsyncTask<Device, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Device... devices) {
            mDevice = devices[0];

            try {
                HttpClient hc = new HttpClient(MainActivity.this);
                HttpResponse response = hc.get(String.format("http://usermgr.jd-app.com/register?name=%s&ip=%s&port=%d",
                        mDevice.name, mDevice.ip, mDevice.port)).execute();
                StringBuilder buf = new StringBuilder();
                response.read(buf);

                JSONObject result = new JSONObject(buf.toString());
                String status = result.getString("status");

                if (status.equals("success")) {
                    return true;
                }
            } catch (HttpClientException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            Log.d(TAG, "registered result = " + result);
            new GetDeviceListTask().execute(mDevice);
        }


    }

    private class GetDeviceListTask extends AsyncTask<Device, Void, List<Device>> {

        @Override
        protected List<Device> doInBackground(Device... localDevices) {
            Device localDevice = localDevices[0];
            ArrayList<Device> deviceList = null;

            try {
                HttpClient hc = new HttpClient(MainActivity.this);
                HttpResponse response = hc.get("http://usermgr.jd-app.com/get").execute();
                StringBuilder buf = new StringBuilder();
                response.read(buf);

                JSONObject result = new JSONObject(buf.toString());
                JSONArray deviceListObj = result.getJSONArray("devices");
                deviceList = new ArrayList<Device>();
                for (int i = 0; i < deviceListObj.length(); i++) {
                    JSONObject deviceObj = deviceListObj.getJSONObject(i);
                    if (!localDevice.name.equals(deviceObj.getString("name"))) {
                        deviceList.add(new Device(deviceObj.getString("name"), deviceObj.getString("ip"), Integer.valueOf(deviceObj.getString("port"))));
                    }
                }
            } catch (HttpClientException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return deviceList;
        }

        @Override
        protected void onPostExecute(List<Device> devices) {
            mDeviceListView.setAdapter(new ArrayAdapter<Device>(MainActivity.this, R.layout.device_list_item, R.id.device_list_item, devices));
        }
    }

    private class ConnectToRemoteDeviceTask extends AsyncTask<Device, Void, Void> {

        @Override
        protected Void doInBackground(Device... devices) {
            Device remoteDevice = devices[0];
            StunClient.getInstance().connectToRemoteDevice(remoteDevice);

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

        }
    }

    Device mDevice;
    private ListView mDeviceListView;
}
