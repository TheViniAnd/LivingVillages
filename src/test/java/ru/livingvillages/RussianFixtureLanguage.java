package ru.livingvillages;

import org.junit.jupiter.api.extension.*;
import ru.livingvillages.i18n.Messages;

/** Historical economy/geometry tests use Russian fixtures; production still defaults to English. */
public final class RussianFixtureLanguage implements BeforeEachCallback,AfterEachCallback {
    private Messages.Scope scope;
    @Override public void beforeEach(ExtensionContext context){scope=Messages.preview("ru");}
    @Override public void afterEach(ExtensionContext context){if(scope!=null)scope.close();}
}
