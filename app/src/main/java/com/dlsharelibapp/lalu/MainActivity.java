package com.dlsharelibapp.lalu;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.dlsharelibapp.lalu.share.receiver.ReceiverActivity;
import com.dlsharelibapp.lalu.share.sender.ShareActivity;
import com.dlsharelibapp.lalu.share.sender.ShareService;
import com.dlsharelibapp.lalu.share.utils.Contants;
import com.github.angads25.filepicker.controller.DialogSelectionListener;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
       setContentView(R.layout.activity_main);
    }


    public void receive(View view) {
       startActivity(new Intent(this, ReceiverActivity.class));

    }

    public void send(View view) {

        shareFile();


    }
    public void shareFile() {

        try {
            DialogProperties properties = new DialogProperties();
            properties.selection_mode = DialogConfigs.MULTI_MODE;
            properties.selection_type = DialogConfigs.FILE_SELECT;
            // TODO: 3/3/18 Root directory
            properties.root = FileSave.contentFile();
            properties.error_dir = new File(DialogConfigs.DEFAULT_DIR);
            properties.extensions = null;

            FilePickerDialog dialog = new FilePickerDialog(this, properties);
            dialog.setTitle("Select files to share");


            dialog.setDialogSelectionListener(new DialogSelectionListener() {
                @Override
                public void onSelectedFilePaths(String[] files) {
                    if (null == files || files.length == 0) {
                        Toast.makeText(MainActivity.this, "Select at least one file to start Share Mode", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(getApplicationContext(), ShareActivity.class);
                    intent.putExtra(ShareService.EXTRA_FILE_PATHS, files);
                    intent.putExtra(ShareService.EXTRA_PORT, Contants.PORT);
                    intent.putExtra(ShareService.EXTRA_SENDER_NAME, Contants.SENDER);
                    startActivity(intent);
                }
            });
            dialog.show();
        }catch (Exception e){e.printStackTrace();}
    }
}
