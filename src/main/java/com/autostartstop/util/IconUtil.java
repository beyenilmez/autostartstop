package com.autostartstop.util;

import com.autostartstop.Log;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Utility class for handling server icons (favicons).
 * Supports loading icons from file paths or base64 strings.
 * 
 * <p>Icons must be PNG format and exactly 64x64 pixels to be valid for Minecraft server list.
 */
public final class IconUtil {
    private static final Logger logger = Log.get(IconUtil.class);
    private static final int REQUIRED_WIDTH = 64;
    private static final int REQUIRED_HEIGHT = 64;
    private static final String DATA_URI_PREFIX = "data:image/png;base64,";
    
    private IconUtil() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Loads an icon as a BufferedImage from a file path or base64 string.
     * 
     * <p>This method is useful when you need the BufferedImage directly (e.g., for Velocity's Favicon.create()).
     * 
     * @param iconInput file path or base64 string
     * @return BufferedImage (64x64 PNG), or null if invalid
     */
    public static BufferedImage loadIconAsImage(String iconInput) {
        if (iconInput == null || iconInput.isEmpty()) {
            return null;
        }
        
        // Check if it's a base64 string
        if (iconInput.startsWith(DATA_URI_PREFIX)) {
            return loadIconFromBase64(iconInput);
        }
        
        // Treat as file path
        return loadImageFromFile(iconInput);
    }
    
    /**
     * Loads an image from a file path.
     * 
     * @param filePath the path to the icon file
     * @return BufferedImage, or null if invalid
     */
    private static BufferedImage loadImageFromFile(String filePath) {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                logger.warn("Icon file does not exist: {}", filePath);
                return null;
            }
            
            if (!Files.isRegularFile(path)) {
                logger.warn("Icon path is not a regular file: {}", filePath);
                return null;
            }
            
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                logger.warn("Failed to read image from file: {}", filePath);
                return null;
            }
            
            // Validate dimensions
            if (image.getWidth() != REQUIRED_WIDTH || image.getHeight() != REQUIRED_HEIGHT) {
                logger.warn("Icon must be exactly {}x{} pixels, but got {}x{}: {}", 
                        REQUIRED_WIDTH, REQUIRED_HEIGHT, 
                        image.getWidth(), image.getHeight(), filePath);
                return null;
            }
            
            return image;
        } catch (IOException e) {
            logger.warn("Error loading icon from file '{}': {}", filePath, e.getMessage());
            logger.debug("Icon loading error details:", e);
            return null;
        } catch (Exception e) {
            logger.warn("Unexpected error loading icon from file '{}': {}", filePath, e.getMessage());
            logger.debug("Icon loading error details:", e);
            return null;
        }
    }
    
    /**
     * Loads an image from a base64 string.
     * 
     * @param base64Input base64 string (with or without data URI prefix)
     * @return BufferedImage, or null if invalid
     */
    private static BufferedImage loadIconFromBase64(String base64Input) {
        try {
            // Remove data URI prefix if present
            String base64Data = base64Input;
            if (base64Data.startsWith(DATA_URI_PREFIX)) {
                base64Data = base64Data.substring(DATA_URI_PREFIX.length());
            }
            
            // Decode base64
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            
            // Read image from bytes
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imageBytes);
            BufferedImage image = ImageIO.read(bais);
            
            if (image == null) {
                logger.warn("Failed to decode image from base64 string");
                return null;
            }
            
            // Validate dimensions
            if (image.getWidth() != REQUIRED_WIDTH || image.getHeight() != REQUIRED_HEIGHT) {
                logger.warn("Icon must be exactly {}x{} pixels, but got {}x{}", 
                        REQUIRED_WIDTH, REQUIRED_HEIGHT, 
                        image.getWidth(), image.getHeight());
                return null;
            }
            
            return image;
        } catch (Exception e) {
            logger.warn("Error loading icon from base64: {}", e.getMessage());
            logger.debug("Base64 icon loading error details:", e);
            return null;
        }
    }
}
