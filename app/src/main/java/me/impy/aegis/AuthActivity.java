package me.impy.aegis;

import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mattprecious.swirl.SwirlView;

import java.lang.reflect.UndeclaredThrowableException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

import me.impy.aegis.crypto.KeyStoreHandle;
import me.impy.aegis.crypto.MasterKey;
import me.impy.aegis.crypto.slots.FingerprintSlot;
import me.impy.aegis.crypto.slots.PasswordSlot;
import me.impy.aegis.crypto.slots.Slot;
import me.impy.aegis.crypto.slots.SlotCollection;
import me.impy.aegis.helpers.FingerprintHelper;
import me.impy.aegis.helpers.FingerprintUiHelper;
import me.impy.aegis.helpers.AuthHelper;

public class AuthActivity extends AegisActivity implements FingerprintUiHelper.Callback, SlotCollectionTask.Callback {
    public static final int RESULT_OK = 0;
    public static final int RESULT_EXCEPTION = 1;

    private EditText _textPassword;

    private SlotCollection _slots;
    private FingerprintUiHelper _fingerHelper;
    private Cipher _fingerCipher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        _textPassword = findViewById(R.id.text_password);
        LinearLayout boxFingerprint = findViewById(R.id.box_fingerprint);
        TextView textFingerprint = findViewById(R.id.text_fingerprint);

        SwirlView imgFingerprint = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ViewGroup insertPoint = findViewById(R.id.img_fingerprint_insert);
            imgFingerprint = new SwirlView(this);
            insertPoint.addView(imgFingerprint, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        Intent intent = getIntent();
        _slots = (SlotCollection) intent.getSerializableExtra("slots");

        // only show the fingerprint controls if the api version is new enough, permission is granted, a scanner is found and a fingerprint slot is found
        FingerprintManager manager = FingerprintHelper.getManager(this);
        if (manager != null && _slots.has(FingerprintSlot.class)) {
            try {
                KeyStoreHandle handle = new KeyStoreHandle();
                if (handle.keyExists()) {
                    SecretKey key = handle.getKey();
                    _fingerCipher = Slot.createCipher(key, Cipher.DECRYPT_MODE);
                    _fingerHelper = new FingerprintUiHelper(manager, imgFingerprint, textFingerprint, this);
                    boxFingerprint.setVisibility(View.VISIBLE);
                }
            } catch (Exception e) {
                throw new UndeclaredThrowableException(e);
            }
        }

        Button button = findViewById(R.id.button_decrypt);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                char[] password = AuthHelper.getPassword(_textPassword, true);
                trySlots(PasswordSlot.class, password);
            }
        });
    }

    @Override
    protected void setPreferredTheme(boolean nightMode) {
        if (nightMode) {
            setTheme(R.style.AppTheme_Dark);
        } else {
            setTheme(R.style.AppTheme_Default);
        }
    }

    private void showError() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Decryption error");
        builder.setMessage("Master key integrity check failed for every slot. Make sure you didn't mistype your password.");
        builder.setCancelable(false);
        builder.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        builder.create().show();
    }

    private <T extends Slot> void trySlots(Class<T> type, Object obj) {
        new SlotCollectionTask<>(type, this, this).execute(new SlotCollectionTask.Params(){{
            Slots = _slots;
            Obj = obj;
        }});
    }

    private void setKey(MasterKey key) {
        // send the master key back to the main activity
        Intent result = new Intent();
        result.putExtra("key", key);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onBackPressed() {
        // ignore back button presses
    }

    @Override
    public void onResume() {
        super.onResume();

        if (_fingerHelper != null) {
            _fingerHelper.startListening(new FingerprintManager.CryptoObject(_fingerCipher));
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (_fingerHelper != null) {
            _fingerHelper.stopListening();
        }
    }

    @Override
    public void onAuthenticated() {
        trySlots(FingerprintSlot.class, _fingerCipher);
    }

    @Override
    public void onError() {

    }

    @Override
    public void onTaskFinished(MasterKey key) {
        if (key != null) {
            setKey(key);
        } else {
            showError();
        }
    }
}
