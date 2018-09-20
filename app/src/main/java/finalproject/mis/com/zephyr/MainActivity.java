package finalproject.mis.com.zephyr;

import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.kircherelectronics.fsensor.filter.averaging.LowPassFilter;
import com.kircherelectronics.fsensor.filter.averaging.MeanFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import static android.hardware.SensorManager.SENSOR_DELAY_NORMAL;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    //example variables
    private double[] freqCounts;

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private List<Entry> xValueList;
    private List<Entry> yValueList;
    private List<Entry> zValueList;
    private List<Entry> magnitudeList;
    private int sampleRate, wSize, axisEntryIndex, magnitudeIndex,doContinueLessThan10, overNightDataTrack;
    private double[] magnitudeArray;
    private Boolean isTrackingOn,trackOvernight;
    private long lastUpdate = 0, lastUpdateOverNight = 0;
    HelperClass helper;
    String oveNightDataFilePath;
    Button btnStart, btnStop;
    Timer stopTimer;
    Handler stopHandler;

    private float[] linearAcceleration = new float[]{ 0, 0, 0 };

    //------------------------------------KalebKE/FSensor-----------------------------------------------
    private MeanFilter meanFilter;
    private LowPassFilter lpfAccelerationSmoothing;
    //------------------------------------KalebKE/FSensor-----------------------------------------------

//    private int count = 0;
//    static final float timeConstant = 0.18f;
//    private float alpha;
//    private float timestamp = System.nanoTime();
//    private float timestampOld = System.nanoTime();
//    private float[] gravity = new float[]{ 0, 0, 0 };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnStart = (Button)findViewById(R.id.startBtn);
        btnStop = (Button)findViewById(R.id.stopBtn);
        btnStop.setEnabled(false);
        isTrackingOn = false;
        trackOvernight = false;
        oveNightDataFilePath="";
        overNightDataTrack=0;
        inititalise();
        storagePermissionStatus();
    }

    public void startTracking( View view){
        axisEntryIndex = 0;
        magnitudeIndex = 0;
        doContinueLessThan10 = 0;
        xValueList = new ArrayList<>();
        yValueList = new ArrayList<>();
        zValueList = new ArrayList<>();
        magnitudeList = new ArrayList<>();
        magnitudeArray = new double[1000];

        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        isTrackingOn = true;
    }

    public Boolean storagePermissionStatus(){

        int RequestCheckResult = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (RequestCheckResult != PackageManager.PERMISSION_GRANTED){

            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE))
            {

                Toast.makeText(MainActivity.this,"Allow Access to Storage", Toast.LENGTH_LONG).show();
                return false;

            } else {

                ActivityCompat.requestPermissions(MainActivity.this,new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

            }
        }
        return true;
    }

    public void OverNightTracking( View view){
        helper = new HelperClass();
        if(storagePermissionStatus()){
            trackOvernight = true;
            isTrackingOn = false;
            oveNightDataFilePath = helper.getFilePath();
            helper.clearCSVFile(oveNightDataFilePath,"x,y,z\n");
            findViewById(R.id.overNightTrackBtn).setEnabled(false);

        }
    }

    public void collectOvernightData(SensorEvent event){
        float x,y,z;
        float[] acceleration;
        float[] input = { event.values[0], event.values[1], event.values[2]};
        acceleration = lpfAccelerationSmoothing.filter(input);
        acceleration = meanFilter.filter(acceleration);
        x = acceleration[0];
        y = acceleration[1];
        z = acceleration[2];

        if( !Float.isNaN(x) && !Float.isNaN(y) && !Float.isNaN(z) ) {

            if (oveNightDataFilePath != "" && overNightDataTrack < 12288) {

                String sensorData = String.valueOf(x) + ","
                        + String.valueOf(y) + ","
                        + String.valueOf(z) + "\n";
                overNightDataTrack++;
                helper.writeToCSVFile(oveNightDataFilePath, sensorData);

            } else if (oveNightDataFilePath != "" && overNightDataTrack == 12288) {
                System.out.println("trackOvernight" + trackOvernight);
                trackOvernight = false;
                System.out.println("trackOvernight" + trackOvernight);
                helper.readCSVFile(oveNightDataFilePath);
                System.out.println("@@@@Inside Read File@@@@@@");
                findViewById(R.id.overNightTrackBtn).setEnabled(true);

            } else if (oveNightDataFilePath == "") {

                Toast.makeText(MainActivity.this, "There was a problem in accessing the CSV file." +
                        " Please delete the folde Zephry and try again. Thank You", Toast.LENGTH_LONG).show();

            }
        }
    }

    /*
        @Purpose: Stop tracking of sensor data
     */
    public void stopTracking( View view){
        reInitialiseChart();
    }

    public void reInitialiseChart(){
        isTrackingOn = false;
        btnStop.setEnabled(false);
        btnStart.setEnabled(true);
        LineChart graph = (LineChart) findViewById(R.id.dataGraph);
        LineData lineData = new LineData();
        graph.notifyDataSetChanged();
        graph.setData(lineData);
    }

    /*
        @Purpose: To initialise the app
                    Check for permissions, if not present ask for permission
     */
    public void inititalise(){
        //sampleRate = 100000; //in microseconds = 1 seconds
        wSize = 128;
        lpfAccelerationSmoothing = new LowPassFilter();
        lpfAccelerationSmoothing.setTimeConstant(0.2f);
        meanFilter = new MeanFilter();
        meanFilter.setTimeConstant(0.8f);
        getSensorData();
    }

    /*
    @Purpose: To get data from Motion Sensor -
     */
    public void getSensorData(){
        boolean isSucess = initialiseSensors();
        //Log.d("@@@Is Sucess: " , String.valueOf(isSucess));
        if( !isSucess ){
            HelperClass.showToastMessage("The device sensor could not be acessed" +
                    " or there is no accelerometer present on device " , this);
        }
    }

    /*
        @Purpose: Initialise Sensor
     */
    public boolean initialiseSensors(){
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null
                && mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null){
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI);
            return true;
        }
        return false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long currTime = System.nanoTime();
        long diff = currTime - lastUpdate;
        if( trackOvernight == true && currTime - lastUpdateOverNight > 1000000000 ){
            lastUpdateOverNight = currTime;
            collectOvernightData(event);
        }
        else if ( diff > 500000000 && isTrackingOn) {
            lastUpdate = currTime;

            if (magnitudeIndex == wSize && isTrackingOn && doContinueLessThan10 < 20) {
                isTrackingOn = false;
                FFTAsynctask fftAsynctask = new FFTAsynctask(wSize);
                fftAsynctask.execute(magnitudeArray);
            } else if ( isTrackingOn && doContinueLessThan10 > 19) {
                HelperClass.showToastMessage("Please place the phone correctly on chest", this);
                isTrackingOn = false;
            }
            if (isTrackingOn) {
                updateChartData(event);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    /*
        @Purpose: To initially remove gravity and remove noise to make a smooth signal
     */
    public float[] filterData(float[] input){
        float[] acceleration = new float[3];
        acceleration = lpfAccelerationSmoothing.filter(input);
        acceleration = meanFilter.filter(acceleration);
        return acceleration;
    }

    /*
        @Purpose: To plot data from accelerometer in grap-view
     */
    public void updateChartData( SensorEvent event ){

        float x,y,z,magnitude;

        float[] input = { event.values[0], event.values[1], event.values[2]};
        float output[] = filterData(input);
        x = output[0];
        y = output[1];
        z = output[2];

        //magnitude = (float) Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
        if( !Float.isNaN(y) &&( y > 5 && y < 10)) {
            magnitudeArray[magnitudeIndex] = y;
            magnitudeIndex++;
            axisEntryIndex += 1;
            //xValueList.add( new Entry( axisEntryIndex, x));
            yValueList.add(new Entry(axisEntryIndex, y));
            //zValueList.add( new Entry( axisEntryIndex, z));
            //magnitudeList.add( new Entry( axisEntryIndex, magnitude));
            updateChartData();
        }
        else if( y < 5 || y > 10){
            doContinueLessThan10++;
        }
    }

    /*
        @Purpose: To update the accelerometer chart with new data
     */
    public void updateChartData(){
        LineChart graph = (LineChart) findViewById(R.id.dataGraph);
        LineData lineData = new LineData();
        //lineData.addDataSet(addLineData( xValueList, "X" , Color.RED));
        lineData.addDataSet(addLineData( yValueList, "Y" , Color.GREEN ));
        //lineData.addDataSet(addLineData( zValueList, "Z" , Color.BLUE));
        //lineData.addDataSet(addLineData( magnitudeList, "Magnitude" , Color.WHITE));
        graph.setBackgroundColor(Color.DKGRAY);
        graph.setGridBackgroundColor(Color.BLACK);
        graph.setData(lineData);
        graph.notifyDataSetChanged();
        graph.invalidate();
    }

    /*
        @Purpose: To create a data set for each line to be shown on graph
        @Return: LineDataSet
    */
    public LineDataSet addLineData( List<Entry> valueList, String label, int lineColor  ){
        LineDataSet lineDataSet = new LineDataSet(valueList, label);
        lineDataSet.setColor(lineColor);
        lineDataSet.setValueTextColor(lineColor);
        lineDataSet.setValueTextSize(1);
        lineDataSet.setDrawCircles(false);
        return lineDataSet;
    }
    /*
        @Purpose: To update the FFT chart with new data
     */
    public void updateFFTChart(){
        LineChart fftGraph = (LineChart) findViewById(R.id.fftGraph);
        List<Entry> magnitudeEntryList = new ArrayList<>();
        Double maxFreqCount = 0.0, sumOfCounts = 0.0, bpm;
        int cnt=0;
        for (int i = 1; i < freqCounts.length; i++){
            magnitudeEntryList.add( new Entry(i, (float) freqCounts[i]));
            if( freqCounts[i] >= 0.1 && freqCounts[i] < 0.6){
                sumOfCounts +=  freqCounts[i];
                cnt++;
            }

            if( maxFreqCount < freqCounts[i]){
                maxFreqCount = freqCounts[i];
            }
        }
        LineData lineData = new LineData();
        lineData.addDataSet(addLineData( magnitudeEntryList, "X" , Color.BLACK));
        fftGraph.setData(lineData);
        fftGraph.notifyDataSetChanged();
        fftGraph.invalidate();
        fftGraph.setDescription(new Description());
        reInitialiseChart();
        bpm = sumOfCounts/cnt;
        bpm = 60*bpm;
        Log.d("Breath Rate is: " , String.valueOf(bpm));

        if( maxFreqCount == 0.0){
            HelperClass.showToastMessage("Please start the process again!!!", this);
        }

        TextView bpmTxt = (TextView) findViewById(R.id.breathRateTxt);
        bpmTxt.setText(String.valueOf(bpm));
    }

    //When user moves out of the app, stop unregister the sensor
//    @Override
//    public void onPause() {
//        super.onPause();  // Always call the superclass method first
//        mSensorManager.unregisterListener(MainActivity.this);
//    }

//    @Override
//    protected void onResume() {
//        super.onResume();
//        mSensorManager.registerListener(MainActivity.this, mSensor, SensorManager.SENSOR_DELAY_UI);
//    }

    /**
     * Implements the fft functionality as an async task
     * FFT(int n): constructor with fft length
     * fft(double[] x, double[] y)
     */

    private class FFTAsynctask extends AsyncTask<double[], Void, double[]> {

        private int wsize; //window size must be power of 2

        // constructor to set window size
        FFTAsynctask(int wsize) {
            this.wsize = wsize;
        }

        @Override
        protected double[] doInBackground(double[]... values) {


            double[] realPart = values[0].clone(); // actual acceleration values
            double[] imagPart = new double[wsize]; // init empty

            /**
             * Init the FFT class with given window size and run it with your input.
             * The fft() function overrides the realPart and imagPart arrays!
             */
            FFT fft = new FFT(wsize);
            fft.fft(realPart, imagPart);
            //init new double array for magnitude (e.g. frequency count)
            double[] magnitude = new double[wsize];


            //fill array with magnitude values of the distribution
            for (int i = 0; wsize > i ; i++) {
                magnitude[i] = Math.sqrt(Math.pow(realPart[i], 2) + Math.pow(imagPart[i], 2));
            }
            return magnitude;
        }

        @Override
        protected void onPostExecute(double[] values) {
            //hand over values to global variable after background task is finished
//            Log.d("##values.length: ", String.valueOf(values.length));
            for(int i=0; i< values.length; i++){
                Log.d("values[" + String.valueOf(i) +"] :-", String.valueOf(values[i]));
            }
            freqCounts = values;
            updateFFTChart();
        }
    }
}