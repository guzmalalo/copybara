package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableSet;
import com.google.copybara.Authoring.AuthoringMappingMode;
import com.google.copybara.Origin.OriginalAuthor;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.testing.DummyOriginalAuthor;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.util.console.testing.TestingConsole.MessageType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AuthoringTest {

  private static final Author DEFAULT_AUTHOR = new Author("Copybara", "no-reply@google.com");

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private SkylarkTestExecutor skylark;
  private OptionsBuilder options;
  private TestingConsole console;

  @Before
  public void setUp() throws Exception {
    options = new OptionsBuilder();
    console = new TestingConsole();
    options.setConsole(console);
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void overwriteTest() throws Exception {
    Authoring authoring = skylark.eval("result",
        "result = authoring.overwrite('foo bar <baz@bar.com>')");
    assertThat(authoring).isEqualTo(new Authoring(new Author("foo bar", "baz@bar.com"),
        AuthoringMappingMode.USE_DEFAULT, ImmutableSet.<String>of()));
  }

  @Test
  public void passThruTest() throws Exception {
    Authoring authoring = skylark.eval("result",
        "result = authoring.pass_thru('foo bar <baz@bar.com>')");
    assertThat(authoring).isEqualTo(new Authoring(new Author("foo bar", "baz@bar.com"),
        AuthoringMappingMode.PASS_THRU, ImmutableSet.<String>of()));
  }

  @Test
  public void whitelistedTest() throws Exception {
    Authoring authoring = skylark.eval("result", ""
        + "result = authoring.whitelisted(\n"
        + "    default = 'foo bar <baz@bar.com>',\n"
        + "    whitelist = ['foo', 'bar'])");
    assertThat(authoring).isEqualTo(new Authoring(new Author("foo bar", "baz@bar.com"),
        AuthoringMappingMode.WHITELIST, ImmutableSet.of("foo", "bar")));
  }

  @Test
  public void testWhitelistMappingDuplicates() throws Exception {
    asssertErrorMessage(""
            + "authoring.whitelisted(\n"
            + "  default = 'Copybara <no-reply@google.com>',\n"
            + "  whitelist = ['foo', 'foo']\n"
            + ")\n",
        "Duplicated whitelist entry 'foo'");
  }

  @Test
  public void testDefaultAuthorNotEmpty() throws Exception {
    asssertErrorMessage("authoring.overwrite()\n",
        "insufficient arguments received by overwrite\\(default: string\\)");
  }


  @Test
  public void testInvalidDefaultAuthor() throws Exception {
    asssertErrorMessage(""
            + "authoring.overwrite(\n"
            + "    default = 'invalid')\n",
        "Author 'invalid' doesn't match the expected format 'name <mail@example.com>");
  }

  @Test
  public void testWhitelistNotEmpty() throws Exception {
    asssertErrorMessage(""
            + "authoring.whitelisted(\n"
            + "  default = 'Copybara <no-reply@google.com>',\n"
            + "  whitelist = []\n"
            + ")\n",
        "'whitelisted' function requires a non-empty 'whitelist' field. "
            + "For default mapping, use 'overwrite\\(...\\)' mode instead.");
  }

  @Test
  public void testResolve_use_default() throws Exception {
    Authoring authoring = new Authoring(
        DEFAULT_AUTHOR, AuthoringMappingMode.USE_DEFAULT, ImmutableSet.<String>of());
    assertThat(authoring.resolve(new DummyOriginalAuthor("foo bar", "baz@bar.com")))
        .isEqualTo(DEFAULT_AUTHOR);
  }

  @Test
  public void testResolve_pass_thru() throws Exception {
    Authoring authoring = new Authoring(
        DEFAULT_AUTHOR, AuthoringMappingMode.PASS_THRU, ImmutableSet.<String>of());
    OriginalAuthor contributor = new DummyOriginalAuthor("foo bar", "baz@bar.com");
    assertThat(authoring.resolve(contributor)).isEqualTo(new Author("foo bar", "baz@bar.com"));
  }

  @Test
  public void testResolve_whitelist() throws Exception {
    Authoring authoring = new Authoring(
        DEFAULT_AUTHOR, AuthoringMappingMode.WHITELIST, ImmutableSet.of("baz@bar.com"));
    OriginalAuthor contributor = new DummyOriginalAuthor("foo bar", "baz@bar.com");
    assertThat(authoring.resolve(contributor)).isEqualTo(new Author("foo bar", "baz@bar.com"));
    assertThat(authoring.resolve(new DummyOriginalAuthor("John", "john@someemail.com")))
        .isEqualTo(DEFAULT_AUTHOR);
  }

  private void asssertErrorMessage(String skylarkCode, final String errorMsg) {
    try {
      skylark.eval("result", "result = " + skylarkCode);
      fail();
    } catch (ConfigValidationException e) {
      console.assertThat().onceInLog(MessageType.ERROR, ".*" + errorMsg + ".*");
    }
  }
}
