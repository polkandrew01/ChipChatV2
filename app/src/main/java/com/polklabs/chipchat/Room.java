package com.polklabs.chipchat;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;
import android.util.Log;
import android.view.SubMenu;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.polklabs.chipchat.backend.ChatRoom;
import com.polklabs.chipchat.backend.Message;
import com.polklabs.chipchat.backend.client;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static com.polklabs.chipchat.gallery.LoadImageTask.calculateInSampleSize;

public class Room extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, Listener {

    Context mContext;

    RecyclerView mMessageList;
    EditText mEditText;
    Button mSendButton;
    Button mAddImage;

    App appState;

    String CHANNEL_ID = "IDK";
    private boolean isActive;

    private List<Message> messageList = new ArrayList<>();
    private MessageAdapter mAdapter;
    private client messageClient;
    private DrawerLayout drawer;
    private NavigationView navigationView;
    private boolean isWrite = false;
    private NFCWriteFragment mNfcWriteFragment;
    private boolean isDialogDisplayed = false;
    private NfcAdapter mNfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        //Setup toolbar ----------------------------------------------------------------------------
        drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        //App references ---------------------------------------------------------------------------
        appState = (App)getApplication();
        mContext = getApplicationContext();

        //Get items from view ----------------------------------------------------------------------
        mMessageList = findViewById(R.id.messageList);
        mEditText = findViewById(R.id.chatBox);
        mSendButton = findViewById(R.id.sendMessage);
        mAddImage = findViewById(R.id.addImage);
        mAdapter = new MessageAdapter(messageList);
        mAdapter.setContext(mContext);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        mMessageList.setLayoutManager(mLayoutManager);
        mMessageList.setItemAnimator(new DefaultItemAnimator());
        mMessageList.setAdapter(mAdapter);

        appState.chatRoom.setListener(new ChatRoom.Listener() {
            @Override
            public void publishText(String text) {
            }

            @Override
            public void returnText(String text) {
            }

            @Override
            public void setList(boolean popular, JSONArray list) {
                setUserList(list);
            }

            @Override
            public void publishMessage(String sender, String body, boolean isPrivate) {
                SimpleDateFormat df = new SimpleDateFormat("HH:mm");
                String date = df.format(Calendar.getInstance().getTime());

                Message message = new Message(body, sender, date);
                if (isPrivate) message.setPrivate();
                messageList.add(message);
                mAdapter.notifyDataSetChanged();
                mMessageList.smoothScrollToPosition(mAdapter.getItemCount() - 1);
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (Build.VERSION.SDK_INT >= 26) {
                    ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect
                            .createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    long[] pattern = {0, 100, 50, 100};
                    v.vibrate(pattern, -1);
                }
                Notify(message);
            }

            @Override
            public void publishImage(String sender, String data) {
                byte[] bitData = Base64.decode(data, Base64.DEFAULT);
                SimpleDateFormat df = new SimpleDateFormat("HH:mm");
                String date = df.format(Calendar.getInstance().getTime());

                Message message = new Message(data, sender, date);
                message.setIsImage();
                Bitmap bitmap = BitmapFactory.decodeByteArray(bitData, 0, bitData.length);
                message.setImage(bitmap);
                messageList.add(message);
                mAdapter.notifyDataSetChanged();
                mMessageList.smoothScrollToPosition(mAdapter.getItemCount() - 1);
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (Build.VERSION.SDK_INT >= 26) {
                    ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect
                            .createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    long[] pattern = {0, 100, 50, 100};
                    v.vibrate(pattern, -1);
                }
                Notify(message);
            }
        });
        appState.chatRoom.loadedRoom = true;

        mMessageList.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom){
                if(bottom < oldBottom){
                    mMessageList.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mMessageList.smoothScrollToPosition(mAdapter.getItemCount()-1);
                        }
                    }, 100);
                }
            }
        });

        if(messageClient == null) {
            messageClient = new client(appState.chatRoom);
            messageClient.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }


        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if(imm != null)
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                if(!mEditText.getText().toString().equals("")) {
                    messageClient.messages.add(mEditText.getText().toString());

                    SimpleDateFormat df = new SimpleDateFormat("HH:mm");
                    String date = df.format(Calendar.getInstance().getTime());

                    Message message = new Message(mEditText.getText().toString(), appState.chatRoom.username+"\t", date);
                    message.setSentByMe();
                    messageList.add(message);
                    mAdapter.notifyDataSetChanged();
                    mMessageList.smoothScrollToPosition(mAdapter.getItemCount()-1);
                }
                mEditText.setText("");
                mEditText.clearFocus();
            }
        });

        mAddImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent1 = new Intent(mContext, GalleryActivity.class);
                startActivityForResult(intent1, 12);
            }
        });

        if(appState.chatRoom != null) {
            ((TextView) navigationView.getHeaderView(0).findViewById(R.id.usernameText)).setText(("Username: \'" + appState.chatRoom.username + "\'"));
            ((TextView) navigationView.getHeaderView(0).findViewById(R.id.locationText)).setText(("Password: \'" + appState.chatRoom.password + "\'"));
        }

        try {
            getActionBar().setTitle("Chat Room: " + appState.chatRoom.name);
        }catch(NullPointerException e){
            Log.d("ChipChat", "Could not set title.");
        }
        try {
            getSupportActionBar().setTitle("Chat Room: " + appState.chatRoom.name);
        }catch(NullPointerException e){
            Log.d("ChipChat", "Could not set title.");
        }

        initNFC();
    }

    private void setUserList(JSONArray list){
        Log.d("ChipChat", "Setting Userlist");
        final Menu menu = navigationView.getMenu();
        menu.clear();
        SubMenu sub1 = menu.addSubMenu("Report/Kick Users");
        for(int i = 1; i < list.length(); i++){
            try {
                MenuItem temp = sub1.add(list.getString(i));
                if(i == 1){
                    temp.setIcon(R.drawable.shield2);
                }
                if(list.getString(i).equals(appState.chatRoom.username)){
                    temp.setIcon(R.drawable.plain_circle);
                }
            }catch(JSONException e){}
        }
        SubMenu sub = menu.addSubMenu("Settings");
        sub.add(0, R.id.writeNFC, 0, "Save room to NFC");
    }

    @Override
    protected void onResume(){
        super.onResume();
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        IntentFilter ndefDetected = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        IntentFilter techDetected = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        IntentFilter[] nfcIntentFilter = new IntentFilter[]{techDetected,tagDetected,ndefDetected};

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        if(mNfcAdapter!= null)
            mNfcAdapter.enableForegroundDispatch(this, pendingIntent, nfcIntentFilter, null);
        isActive = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mNfcAdapter!= null)
            mNfcAdapter.disableForegroundDispatch(this);
        isActive = false;
    }

    @Override
    protected void onDestroy(){
        messageClient.stop = true;
        super.onDestroy();
    }

    //Result from choose image ---------------------------------------------------------------------
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == 12){
            if(resultCode == RESULT_OK){
                String bitmapPath = data.getStringExtra("path");
                if(bitmapPath.equals(""))return;
                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(bitmapPath, options);

                //Adjust sample size
                //options.inSampleSize = calculateInSampleSize(options, GalleryActivity.size/3, GalleryActivity.size/3);
                options.inSampleSize = calculateInSampleSize(options, 200, 200);

                //Return sampled bitmap
                options.inJustDecodeBounds = false;
                Bitmap image = BitmapFactory.decodeFile(bitmapPath, options);
                Log.d("ChipChat", "Image bytes: "+image.getByteCount());
                messageClient.images.add(image);

                SimpleDateFormat df = new SimpleDateFormat("HH:mm");
                String date = df.format(Calendar.getInstance().getTime());

                Message message = new Message("Sent image: "+bitmapPath, appState.chatRoom.username, date);
                message.setSentByMe();
                message.setIsImage();
                message.setImage(image);
                messageList.add(message);
                mAdapter.notifyDataSetChanged();
                mMessageList.smoothScrollToPosition(mAdapter.getItemCount()-1);
            }
        }else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.room, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.sideBar) {
            if(drawer.isDrawerOpen(GravityCompat.END)) {
                drawer.closeDrawer(GravityCompat.END);
            }else{
                drawer.openDrawer(GravityCompat.END);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onNewIntent(Intent intent) {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

        Log.d("ChipChat", "onNewIntent: "+intent.getAction());

        if(tag != null) {

            Ndef ndef = Ndef.get(tag);

            if (isDialogDisplayed) {

                if (isWrite) {

                    String messageToWrite = appState.chatRoom.name + ";"+ appState.chatRoom.password+";"+ appState.chatRoom.local + ";" + appState.chatRoom.unListed;

                    mNfcWriteFragment = (NFCWriteFragment) getFragmentManager().findFragmentByTag(NFCWriteFragment.TAG);
                    mNfcWriteFragment.onNfcDetected(ndef,messageToWrite);


                }
            }
        }
    }


    private void initNFC(){

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
    }

    private void showWriteFragment() {

        Log.d("ChipChat", "Waiting for nfc");

        isWrite = true;

        mNfcWriteFragment = (NFCWriteFragment) getFragmentManager().findFragmentByTag(NFCWriteFragment.TAG);

        if (mNfcWriteFragment == null) {

            mNfcWriteFragment = NFCWriteFragment.newInstance();
        }
        mNfcWriteFragment.show(getFragmentManager(),NFCWriteFragment.TAG);

    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        if(id == R.id.writeNFC){
            //Toast.makeText(mContext, "Writing NFC...", Toast.LENGTH_SHORT).show();
            showWriteFragment();

        }else{
            if(!appState.chatRoom.username.equals(item.getTitle().toString())) {
                UserDialogFragment options = new UserDialogFragment();
                options.setmContext(mContext)
                        .setUsername(item.getTitle().toString())
                        .setMessageClient(messageClient)
                        .setChatRoom(appState.chatRoom)
                        .setmAdapter(mAdapter)
                        .setMessageList(messageList);
                options.show(getFragmentManager(), "User Dialog");
            }
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.END);
        return true;
    }

    @Override
    public void onDialogDisplayed() {

        isDialogDisplayed = true;
    }

    @Override
    public void onDialogDismissed() {

        isDialogDisplayed = false;
        isWrite = false;
    }

    private void Notify(Message message) {
        // notification implementation
        if(!isActive) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setAutoCancel(true)
                    .setSmallIcon(R.drawable.tortilla_chip_kct_icon)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentTitle("Chip Chat")
                    .setContentText(message.getUser() + ": "+ (message.isImage()?"Image":message.getMessage()));

            notificationManager.notify(/*notification id*/1, notificationBuilder.build());
        }
    }
}
