package com.ultraaudio.recorder.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.security.MessageDigest;

/**
 * Utility to generate SHA-256 hashes for audio recordings.
 * Saves hashes in a dedicated "Hash Files" subdirectory.
 */
public class HashUtil {
    public static void generateSHA256(File audioFile) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            FileInputStream fis = new FileInputStream(audioFile);
            byte[] byteArray = new byte[1024];
            int bytesCount;
            
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
            fis.close();
            
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
            }
            
            String hash = sb.toString();
            saveHashToFile(audioFile, hash);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void saveHashToFile(File audioFile, String hash) {
        try {
            File parent = audioFile.getParentFile().getParentFile();
            File hashDir = new File(parent, "Hash Files");
            if (!hashDir.exists()) hashDir.mkdirs();
            
            File hashFile = new File(hashDir, audioFile.getName() + ".sha256");
            FileWriter writer = new FileWriter(hashFile);
            writer.write(hash);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
