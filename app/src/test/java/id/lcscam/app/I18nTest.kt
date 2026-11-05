package id.lcscam.app

import id.lcscam.app.i18n.AppLanguage
import id.lcscam.app.i18n.I18n
import id.lcscam.app.i18n.Lang
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class I18nTest {
    @Test fun systemFollowsLanguageAndRegion() {
        assertEquals(Lang.ID, I18n.resolve(AppLanguage.SYSTEM, Locale("in", "ID")))
        assertEquals(Lang.ID, I18n.resolve(AppLanguage.SYSTEM, Locale("id")))
        assertEquals(Lang.ID, I18n.resolve(AppLanguage.SYSTEM, Locale("en", "ID"))) // English UI, Indonesian region
        assertEquals(Lang.EN, I18n.resolve(AppLanguage.SYSTEM, Locale("en", "US")))
        assertEquals(Lang.EN, I18n.resolve(AppLanguage.SYSTEM, Locale("de", "DE")))
    }

    @Test fun explicitChoiceWins() {
        assertEquals(Lang.EN, I18n.resolve(AppLanguage.ENGLISH, Locale("in", "ID")))
        assertEquals(Lang.ID, I18n.resolve(AppLanguage.INDONESIAN, Locale("en", "US")))
    }
}
