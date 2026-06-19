package com.anime.magic;

import com.anime.magic.core.MessageService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageServiceTest {
    @Test void translatesAmpersandCodes() {
        assertEquals("§cRed", MessageService.translate("&cRed"));
        assertEquals("§aGreen§r and §bBlue", MessageService.translate("&aGreen&r and &bBlue"));
    }
    @Test void emptyStringStaysEmpty() {
        assertEquals("", MessageService.translate(""));
        assertEquals("", MessageService.translate(null));
    }
}
