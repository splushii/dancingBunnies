package se.splushii.dancingbunnies.util;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class Util {
    static final String LOG_CONTEXT = "Util";

    public static String md5(String in) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Log.d(LOG_CONTEXT, "MD5 not supported");
            e.printStackTrace();
            return "";
            // TODO
        }
        try {
            digest.update(in.getBytes("UTF-8"), 0, in.length());
        } catch (UnsupportedEncodingException e) {
            Log.d(LOG_CONTEXT, "UTF-8 not supported");
            e.printStackTrace();
            return "";
            // TODO
        }
        byte[] hash = digest.digest();
        return hex(hash);
    }

    public static String getSalt(SecureRandom rand, int length) {
        byte[] saltBytes = new byte[length / 2]; // Because later hex'd
        rand.nextBytes(saltBytes);
        return hex(saltBytes);
    }

    public static String hex(byte[] in) {
        return String.format("%0" + (in.length * 2) + "x", new BigInteger(1, in));
    }
}
