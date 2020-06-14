/*
 * Copyright © 2020 Jay@Prime-HQ
 */

package net.prime_hq.face_analytic_expression;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.hardware.Camera;
import android.net.SSLCertificateSocketFactory;
import android.net.SSLSessionCache;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.face_analytic_expression.R;

import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String urlLocal = "https://10.0.2.2/dl";
    private static final String urlCloud = "https://10.0.2.2/stream";
    private Camera mCamera;
    private CameraPreview mPreview;

    private static int timer = 0;
    private static int prev_width = 0;
    private static int screen_width = 0;
    private static float pic_ratio = 0.0f;
    private boolean onLayoutChange_status = false;
    private static SharedPreferences sharedPref;

    static long delayTime = 0;
    static Handler timerHandler = new Handler();

    Button captureButton;
    Button dlButton;
    Button okButton;
    RadioButton localPredict;
    RadioButton cloudPredict;
    TextView textLocal;
    TextView textCloud;
    TextView textResponse;
    ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPref = getSharedPreferences("myPref", MODE_PRIVATE);

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) ==PackageManager.PERMISSION_GRANTED) {
            init_app();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA},1);
        }
    }

    private  void init_app(){
        try {
            mCamera = Camera.open();
        }
        catch (Exception ignored){}

        mPreview = new CameraPreview(this, mCamera);
        FrameLayout preview = findViewById(R.id.cam_layout);

        Camera.Parameters parameters = mCamera.getParameters();
        Camera.Size ps = parameters.getPictureSize();

        pic_ratio = (float)ps.width/(float)ps.height;

        preview.addView(mPreview);
        textResponse = findViewById(R.id.textView_response);

        preview.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (onLayoutChange_status) return;

                Camera.Size cs = mCamera.getParameters().getPreviewSize();
                int width = (int) (((float)cs.width/(float)cs.height)*(float)bottom-top);

                prev_width = width;
                screen_width = right-left;

                v.getLayoutParams().width = width;
                v.requestLayout();

                mPreview.getLayoutParams().width = width;
                mPreview.requestLayout();

                onLayoutChange_status = true;
            }
        });

        captureButton = findViewById(R.id.button_take);
        captureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mCamera.takePicture(null, null, mPreview);
                    }
                }
        );

        final Button prefButton = findViewById(R.id.button_pref);
        prefButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Dialog prefDialog = new PrefDialog(v.getContext());
                        prefDialog.setCanceledOnTouchOutside(false);
                        prefDialog.setCancelable(false);
                        prefDialog.show();
                    }
                }
        );

        Button timerButton = findViewById(R.id.button_auto);
        timerButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        TextView tv = findViewById(R.id.textView_auto_set);
                        timerHandler.removeCallbacks(timerRunnable);

                        switch (timer){
                            case 0:
                                timer = 3;
                                tv.setText(R.string.timer_3s);
                                delayTime = 3000;
                                timerHandler.postDelayed(timerRunnable, delayTime);
                                break;
                            case 3:
                                timer = 5;
                                tv.setText(R.string.timer_5s);
                                delayTime = 5000;
                                timerHandler.postDelayed(timerRunnable, delayTime);
                                break;
                            case 5:
                                timer = 0;
                                tv.setText(R.string.timer_off);
                                delayTime = 0;
                        }
                    }
                }
        );
    }

    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            captureButton.performClick();
        }
    };
    Runnable permissionRunnable = new Runnable() {
        @Override
        public void run() {
            finishAffinity();
            System.exit(0);
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            if ((grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                init_app();
            } else {
                Toast.makeText(MainActivity.this, "Permission denied to access your Camera, exiting...", Toast.LENGTH_SHORT).show();
                timerHandler.postDelayed(permissionRunnable, 2000);
            }
        }
    }

    public class PrefDialog extends Dialog {
        Context context;

        public PrefDialog(@NonNull Context context) {
            super(context);
            this.context = context;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.pref_dialog);

            ((RadioButton)findViewById(R.id.cloud_predict)).setChecked(sharedPref.getBoolean("cloud", true));
            ((TextView)findViewById(R.id.editText_cloud)).setText(sharedPref.getString("url", ""));
            ((TextView)findViewById(R.id.editText_local)).setText(sharedPref.getString("urlL", ""));

            mProgressBar = findViewById(R.id.progressBar);
            okButton = findViewById(R.id.ok_button);
            dlButton = findViewById(R.id.button_local);
            textCloud = findViewById(R.id.editText_cloud);
            textLocal = findViewById(R.id.editText_local);
            localPredict = findViewById(R.id.local_predict);
            cloudPredict = findViewById(R.id.cloud_predict);
            dlButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dlButton.setEnabled(false);
                            okButton.setEnabled(false);
                            textCloud.setEnabled(false);
                            textLocal.setEnabled(false);
                            localPredict.setEnabled(false);
                            cloudPredict.setEnabled(false);
                            mProgressBar.setVisibility(View.VISIBLE);
                            Toast toast = Toast.makeText(context, "Downloading model...", Toast.LENGTH_LONG);
                            toast.show();
                            new MyAsyncDLTask(context).execute(null, null, null);
                        }
                    }
            );

            File file = new File(context.getFilesDir(), "model.tflite");
            if(file.exists()) {
                dlButton.setText(R.string.tfLiteUp);
            }

            okButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            sharedPref.edit().putBoolean("cloud", ((RadioButton)findViewById(R.id.cloud_predict)).isChecked()).apply();
                            sharedPref.edit().putString("url", ((TextView)findViewById(R.id.editText_cloud)).getText().toString()).apply();
                            sharedPref.edit().putString("urlL", ((TextView)findViewById(R.id.editText_local)).getText().toString()).apply();
                            dismiss();
                        }
                    }
            );
        }
    }

    static class MyAsyncDLTask extends AsyncTask<Void, Void, String> {
        private WeakReference<MainActivity> mActivity;

        public MyAsyncDLTask(Context a) {
            this.mActivity = new WeakReference<>((MainActivity) a);
        }

        @SuppressLint({"SSLCertificateSocketFactoryGetInsecure", "AllowAllHostnameVerifier"})
        @Override
        protected String doInBackground(Void... params) {
            URL url;
            HttpURLConnection con;
            try {
                url = new URL(sharedPref.getString("url", urlLocal));
                con = (HttpURLConnection) url.openConnection();
                if (con instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) con).setSSLSocketFactory(SSLCertificateSocketFactory.getInsecure(0, new SSLSessionCache(mActivity.get())));
                    ((HttpsURLConnection) con).setHostnameVerifier(new AllowAllHostnameVerifier());
                }
                int response = con.getResponseCode();
                if (response == 200) {
                    BufferedInputStream in = new BufferedInputStream(con.getInputStream());
                    FileOutputStream output = mActivity.get().openFileOutput("model.tflite", Context.MODE_PRIVATE);
                    byte[] buffer = new byte[16*1024];

                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }

                    output.flush();
                    in.close();
                    output.close();
                } else throw new IOException();
            } catch (IOException e) {
                e.printStackTrace();
                return "Download failed!";
            }
            con.disconnect();

            return "Model downloaded!";
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            MainActivity activity = mActivity.get();
            Toast toast = Toast.makeText(activity, s, Toast.LENGTH_LONG);
            toast.show();
            activity.dlButton.setEnabled(true);
            if (s.equals("Model downloaded!")) activity.dlButton.setText(R.string.tfLiteUp);
            activity.okButton.setEnabled(true);
            activity.textCloud.setEnabled(true);
            activity.textLocal.setEnabled(true);
            activity.localPredict.setEnabled(true);
            activity.cloudPredict.setEnabled(true);
            activity.mProgressBar.setVisibility(View.GONE);
        }
    }

    /** A basic Camera preview class */
    @SuppressLint("ViewConstructor")
    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PictureCallback {
        //private byte[] pBuffer;
        private SurfaceHolder mHolder;
        private Camera mCamera;

        public CameraPreview(Context context, Camera camera) {
            super(context);
            mCamera = camera;

            mHolder = getHolder();
            mHolder.addCallback(this);
        }

        public void surfaceCreated(SurfaceHolder holder) {
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            } catch (IOException ignored) {}
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // empty. Take care of releasing the Camera preview in your activity.
            mCamera.stopPreview();
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            if (mHolder.getSurface() == null) return;

            try {
                mCamera.stopPreview();
            } catch (Exception ignored){}

            try {
                mCamera.setPreviewDisplay(mHolder);
                mCamera.startPreview();
            } catch (Exception e){
                Log.d(TAG, "Error starting camera preview: " + e.getMessage());
            }
        }

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            mCamera.stopPreview();

            Activity parentActivity = (Activity)this.getContext();
            Bitmap picture = BitmapFactory.decodeByteArray(data, 0, data.length);
            int croppedSize = (int) (((float)picture.getWidth()*pic_ratio)/((float)prev_width/(float)screen_width));
            Bitmap croppedBitmap = Bitmap.createBitmap(picture, (picture.getWidth()/2)-(croppedSize/2), (picture.getHeight()/2)-(croppedSize/2), croppedSize, croppedSize, null, true);
            picture.recycle();
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, 48, 48, true);
            croppedBitmap.recycle();

            Toast toast = Toast.makeText(parentActivity, "Picture taken..", Toast.LENGTH_LONG);
            toast.show();

            View cl = (View) getParent().getParent();
            ImageView iv = cl.findViewById(R.id.overlay_Layout).findViewById(R.id.imageView_face);
            if (iv!=null) iv.setImageBitmap(scaledBitmap);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            new MyAsyncTask(parentActivity).execute(out, null, null);

            mCamera.startPreview();
            if (delayTime!=0) timerHandler.postDelayed(timerRunnable, delayTime);
        }
    }

    static class MyAsyncTask extends AsyncTask<ByteArrayOutputStream, Void, String> {
        private WeakReference<MainActivity> mActivity;

        public MyAsyncTask(Activity a) {
            this.mActivity = new WeakReference<>((MainActivity) a);
        }

        @SuppressLint({"AllowAllHostnameVerifier", "SSLCertificateSocketFactoryGetInsecure"})
        @Override
        protected String doInBackground(ByteArrayOutputStream... bitmap) {
            if (sharedPref.getBoolean("cloud", true)) {
                int respCode;
                HttpURLConnection connection;

                try {
                    URL url = new URL(sharedPref.getString("url", urlCloud));
                    connection = (HttpURLConnection) url.openConnection();

                    if (connection instanceof HttpsURLConnection) {
                        ((HttpsURLConnection) connection).setSSLSocketFactory(SSLCertificateSocketFactory.getInsecure(0, null));
                        ((HttpsURLConnection) connection).setHostnameVerifier(new AllowAllHostnameVerifier());
                    }

                    byte[] array = bitmap[0].toByteArray();
                    String boundary = UUID.randomUUID().toString();

                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

                    DataOutputStream request = new DataOutputStream(connection.getOutputStream());
                    request.writeBytes("--" + boundary + "\r\n");
                    request.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + System.currentTimeMillis() + ".jpg" + "\"\r\n");
                    request.writeBytes("Content-Type: image/jpeg\r\n");
                    request.writeBytes("Content-Transfer-Encoding: binary\r\n\r\n");
                    request.write(array);
                    request.writeBytes("\r\n");
                    request.writeBytes("--" + boundary + "--\r\n");
                    request.flush();

                    respCode = connection.getResponseCode();
                    if (respCode == 200) {
                        InputStream iStream = connection.getInputStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(iStream));
                        StringBuilder sb = new StringBuilder();
                        String line;

                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }

                        iStream.close();
                        JSONObject obj = new JSONObject(sb.toString());
                        return obj.getString("expression");
                    }
                } catch (Exception ignored) {}
            } else {
                Bitmap picture = BitmapFactory.decodeByteArray(bitmap[0].toByteArray(), 0, bitmap[0].toByteArray().length);
                Bitmap grayPic = Bitmap.createBitmap(picture.getWidth(), picture.getHeight(), picture.getConfig());
                Canvas c = new Canvas(grayPic);
                Paint paint = new Paint();
                ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(0);
                ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
                paint.setColorFilter(f);
                c.drawBitmap(picture, 0, 0, paint);
                picture.recycle();



                try {
                    File file = new File(mActivity.get().getFilesDir(), "model.tflite");
                    Interpreter tfLite = new Interpreter(file);

                    int imageTensorIndex = 0;
                    DataType imageDataType = tfLite.getInputTensor(imageTensorIndex).dataType();
                    int probabilityTensorIndex = 0;
                    int[] probabilityShape = tfLite.getOutputTensor(probabilityTensorIndex).shape();
                    DataType probabilityDataType = tfLite.getOutputTensor(probabilityTensorIndex).dataType();

                    TensorImage inputImageBuffer = new TensorImage(imageDataType);
                    inputImageBuffer.load(grayPic);
                    TensorBuffer probabilityBuffer = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);

                    float[] ff = inputImageBuffer.getTensorBuffer().getFloatArray();
                    float[] gg = new float[ff.length/3];
                    for(int i=0;i<gg.length;i++){
                        gg[i] = ff[i*3]/255.0f;
                    }

                    TensorBuffer tb = TensorBuffer.createFixedSize(new int[]{1,48,48,1}, imageDataType);
                    tb.loadArray(gg);

                    tfLite.run(tb.getBuffer(), probabilityBuffer.getBuffer());

                    List<String> labels;
                    JSONObject obj = null;
                    try {
                        labels = FileUtil.loadLabels(mActivity.get(), "model_dict");
                        obj = new JSONObject(labels.get(0));
                    } catch (JSONException | IOException ignored) {}

                    float l = 0.0f;
                    int L = 0;
                    for (int i = 0; i <probabilityBuffer.getFloatArray().length ; i++)
                        if(l<probabilityBuffer.getFloatArray()[i]) { l = probabilityBuffer.getFloatArray()[i]; L = i; }
                    if (obj!=null) {
                        Iterator<String> it = obj.keys();
                        while(it.hasNext()) {
                            String key = it.next();
                            try {
                                if (obj.getInt(key)==L) { return key; }
                            } catch (JSONException ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }
            return "Failed to do local or cloud prediction!";
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            MainActivity activity = mActivity.get();
            activity.textResponse.setText(s);
        }
    }
}