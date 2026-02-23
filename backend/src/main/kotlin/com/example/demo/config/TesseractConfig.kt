package com.example.demo.config

import java.io.File
import net.sourceforge.tess4j.ITesseract
import net.sourceforge.tess4j.Tesseract
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TesseractConfig {

    @Bean
    fun tesseract(): ITesseract {
        val tesseract = Tesseract()
        // tessdata path can be configured here if needed.
        // By default, it looks for 'tessdata' in the project root or classpath.
        // For local development, we might need to specify the path if not in default location.
        // Example: tesseract.setDatapath("/usr/local/share/tessdata")

        // Ensure tessdata directory exists in the project root if using default
        val tessDataFolder = File("tessdata")
        if (!tessDataFolder.exists()) {
            tessDataFolder.mkdirs()
        }

        tesseract.setDatapath(tessDataFolder.absolutePath)
        tesseract.setLanguage("kor+eng") // Support both Korean and English
        return tesseract
    }
}
