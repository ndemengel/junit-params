package ndemengel;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import ndemengel.Parameters.ParameterizedMethodRunner;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.mockito.ArgumentMatcher;

@SuppressWarnings("unused")
public class ParametersTest {

	RunListener listener = mock(RunListener.class);

	RunNotifier notifier = new RunNotifier();
	{
		notifier.addListener(listener);
	}

	public static class RegularTestCase {
		@Test
		public void regularTest() {
			assertTrue(true);
		}
	}

	@Test
	public void should_run_regular_test() throws Exception {
		new Parameters(RegularTestCase.class).run(notifier);
	}

	public static class TestCaseWithNonParamArgs {
		@Test
		public void testWithArgs(String arg1, int arg2) {
		}
	}

	@Test(expected = InitializationError.class)
	public void should_reject_test_method_with_args_and_no_params_annotation() throws Exception {
		new Parameters(TestCaseWithNonParamArgs.class);
	}

	public static class TestCaseWithNonStaticParamField {

		Object[][] parameters = { { "param" } };

		@Test
		@WithParameters("parameters")
		public void parameterizedTest(String arg) {
		}
	}

	@Test
	public void should_reject_non_static_param_field() throws Exception {
		try {
			new Parameters(TestCaseWithNonStaticParamField.class);
		} catch (InitializationError e) {
			assertThat(e.getCauses().get(0)).hasMessage("Field \"parameters\" must be static");
		}
	}

	public static class TestCaseWithMissingParamField {

		@Test
		@WithParameters("paramField")
		public void parameterizedTest(String arg) {
		}
	}

	@Test
	public void should_reject_missing_param_field() throws Exception {
		try {
			new Parameters(TestCaseWithMissingParamField.class);
		} catch (InitializationError e) {
			assertThat(e.getCauses().get(0)).hasMessage("Field \"paramField\" not found");
		}
	}

	public static class TestCaseWithEmptyParams {

		public static Object[][] parameters = {};

		@Test
		@WithParameters("parameters")
		public void parameterizedTest(String arg) {
		}
	}

	@Test
	public void should_reject_empty_params() throws Exception {
		try {
			new Parameters(TestCaseWithEmptyParams.class);
		} catch (InitializationError e) {
			assertThat(e.getCauses().get(0)).hasMessage("Field \"parameters\" was expected to define parameters");
		}
	}

	public static class TestCaseWithEmptyArgs {

		public static Object[][] parameters = { { "unused" } };

		@Test
		@WithParameters("parameters")
		public void parameterizedTest() {
		}
	}

	@Test
	public void should_reject_test_nethod_without_args() throws Exception {
		try {
			new Parameters(TestCaseWithEmptyArgs.class);
		} catch (InitializationError e) {
			assertThat(e.getCauses().get(0)).hasMessage("Method \"parameterizedTest\" takes no arguments");
		}
	}

	public static class TestCaseWithWrongNumberOfParams {

		public static Object[][] parameters = { { 3 } };

		@Test
		@WithParameters("parameters")
		public void parameterizedTest(Integer arg1, Integer arg2) {
		}
	}

	@Test
	public void should_reject_wrong_number_of_params() throws Exception {
		try {
			new Parameters(TestCaseWithWrongNumberOfParams.class);
		} catch (InitializationError e) {
			assertThat(e.getCauses().get(0)).hasMessage("Method \"parameterizedTest\" requires 2 parameters, but 1 is defined");
		}
	}

	public static class TestCaseWithWrongNumberOfParams2 {

		public static Object[][] parameters = { { 3, 8, 1 } };

		@Test
		@WithParameters("parameters")
		public void parameterizedTest(Integer arg) {
		}
	}

	@Test
	public void should_reject_wrong_number_of_params_2() throws Exception {
		try {
			new Parameters(TestCaseWithWrongNumberOfParams2.class);
		} catch (InitializationError e) {
			assertThat(e.getCauses().get(0)).hasMessage("Method \"parameterizedTest\" requires 1 parameter, but 3 are defined");
		}
	}

	public static class TestCaseWithWronglyTypedParam {

		public static Object[][] parameters = { { 8.9, 3 } };

		@Test
		@WithParameters("parameters")
		public void parameterizedTest(String arg1, Integer arg2) {
		}
	}

	@Test
	public void should_reject_param_with_wrong_type() throws Exception {
		try {
			new Parameters(TestCaseWithWronglyTypedParam.class);
		} catch (InitializationError e) {
			assertThat(e.getCauses().get(0)).hasMessage("Parameter <8.9> should be a <class java.lang.String>");
		}
	}

	public static class TestCaseWithPrimitiveParam {

		public static int[][] parameters = { { 7 } };

		@Test
		@WithParameters("parameters")
		public void parameterizedTest(int arg1) {
		}
	}

	@Test
	public void should_warn_about_primitive_param() throws Exception {
		try {
			new Parameters(TestCaseWithPrimitiveParam.class);
		} catch (InitializationError e) {
			assertThat(e.getCauses().get(0)).hasMessage("Parameter fields must be arrays of non primitive types (for now)");
		}
	}

	public static class TestCaseWithParams {

		// should be accessed although it is private
		private static Object[][] myParams = { { "run1param1", "run1param2" }
				, { "run2param1", "run2param2" } };

		@Test
		@WithParameters("myParams")
		public void testWithParams(String arg1, String arg2) {
			if (arg1.equals("run1param1")) {
				assertThat(arg2).isEqualTo("run1param2");
			}
			else {
				assertThat(arg1).isEqualTo("run2param1");
				assertThat(arg2).isEqualTo("run2param2");
			}
		}
	}

	@Test
	public void should_give_referenced_parameters_to_test_method_annotated_with_params() throws Exception {
		// given
		Parameters runner = new Parameters(TestCaseWithParams.class);

		// when
		runner.run(notifier);

		// then
		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams run1param1, run1param2"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams run1param1, run1param2"));

		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams run2param1, run2param2"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams run2param1, run2param2"));

		verifyNoMoreInteractions(listener);
	}

	public static class TestCaseWithParamsAndOneFailure {

		static int methodCallCount;

		static Integer[][] someParams = { { 1 }, { 2 }, { 3 } };

		@Test
		@WithParameters("someParams")
		public void testWithParams(Integer arg) {
			if (methodCallCount++ == 1) {
				fail("test failure");
			}
		}
	}

	@Test
	public void should_report_test_failure_for_right_parameters() throws Exception {
		// given
		Parameters runner = new Parameters(TestCaseWithParamsAndOneFailure.class);

		// when
		runner.run(notifier);

		// then
		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams 1"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams 1"));

		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams 2"));
		verify(listener).testFailure(anyFailureWithDescriptionStartingWithAndMessage("testWithParams 2", "test failure"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams 2"));

		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams 3"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams 3"));

		verifyNoMoreInteractions(listener);
	}

	public static class TestCaseWithIgnoredParameterizedTest {

		static Integer[][] someParams = { { 1 }, { 2 }, { 3 } };

		@Test
		@Ignore
		@WithParameters("someParams")
		public void testWithParams(Integer arg) {
			if (arg == 2) {
				fail("test failure");
			}
		}
	}

	@Test
	public void should_report_ignored_parameterized_tests() throws Exception {
		// given
		Parameters runner = new Parameters(TestCaseWithIgnoredParameterizedTest.class);

		// when
		runner.run(notifier);

		// then
		verify(listener).testIgnored(anyDescriptionStartingWith("testWithParams 1"));
		verify(listener).testIgnored(anyDescriptionStartingWith("testWithParams 2"));
		verify(listener).testIgnored(anyDescriptionStartingWith("testWithParams 3"));

		verifyNoMoreInteractions(listener);
	}

	public static class ParameterizedTestCaseWithExpectedException {

		static Integer[][] someParams = { { 1 }, { 2 }, { 3 } };

		@Test(expected = RuntimeException.class)
		@WithParameters("someParams")
		public void testWithParams(Integer arg) {
			if (arg == 1) {
				throw new RuntimeException("expected exception");
			}
		}
	}

	@Test
	public void should_allow_for_expecting_exceptions() throws Exception {
		// given
		Parameters runner = new Parameters(ParameterizedTestCaseWithExpectedException.class);

		// when
		runner.run(notifier);

		// then
		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams 1"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams 1"));

		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams 2"));
		verify(listener).testFailure(anyFailureWithDescriptionStartingWithAndExpectedException("testWithParams 2", RuntimeException.class));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams 2"));

		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams 3"));
		verify(listener).testFailure(anyFailureWithDescriptionStartingWithAndExpectedException("testWithParams 3", RuntimeException.class));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams 3"));

		verifyNoMoreInteractions(listener);
	}

	public static class ParameterizedTestCaseWithTimeout {

		static Integer[][] someParams = { { 1 }, { 2 }, { 3 } };

		@Test(timeout = 50)
		@WithParameters("someParams")
		public void testWithParams(Integer arg) throws Exception {
			if (arg != 3) {
				Thread.sleep(51);
			}
		}
	}

	@Test
	public void should_allow_for_defining_timeout() throws Exception {
		// given
		Parameters runner = new Parameters(ParameterizedTestCaseWithTimeout.class);

		// when
		runner.run(notifier);

		// then
		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams 1"));
		verify(listener).testFailure(anyFailureWithDescriptionStartingWithAndMessage("testWithParams 1", "test timed out after 50 milliseconds"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams 1"));

		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams 2"));
		verify(listener).testFailure(anyFailureWithDescriptionStartingWithAndMessage("testWithParams 2", "test timed out after 50 milliseconds"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams 2"));

		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams 3"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams 3"));

		verifyNoMoreInteractions(listener);
	}

	@Test
	public void should_allow_for_re_running_a_parameterized_method_with_a_given_parameter_sets() throws Exception {
		// given
		Parameters runner = new Parameters(TestCaseWithParams.class);

		ParameterizedMethodRunner methodRunner = (ParameterizedMethodRunner) runner.getChildren().get(0);
		methodRunner.filter(new KeepTestsWithDescriptionStartingWith("testWithParams run2"));

		// when
		runner.run(notifier);

		// then
		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams run2"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams run2"));

		verifyNoMoreInteractions(listener);
	}

	public static class TestCaseWithAndWithoutParams {

		static Object[][] runs = { { "run1" }, { "run2" } };

		@Test
		@WithParameters("runs")
		public void testWithParams(String arg) {
		}

		@Test
		public void testWithoutParams() {
		}
	}

	@Test
	public void should_allow_for_re_running_a_parameterized_method_with_all_parameter_sets_when_other_test_methods_are_defined() throws Exception {
		// given
		Parameters runner = new Parameters(TestCaseWithAndWithoutParams.class);

		runner.filter(new KeepTestsWithDescriptionStartingWith("testWithParams"));

		// when
		runner.run(notifier);

		// then
		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams run1"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams run1"));

		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams run2"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams run2"));

		verifyNoMoreInteractions(listener);
	}

	@Test
	public void should_allow_for_re_running_a_regular_test_method_when_parameterized_methods_are_defined() throws Exception {
		// given
		Parameters runner = new Parameters(TestCaseWithAndWithoutParams.class);

		runner.filter(new KeepTestsWithDescriptionStartingWith("testWithoutParams"));

		// when
		runner.run(notifier);

		// then
		verify(listener).testStarted(anyDescriptionStartingWith("testWithoutParams"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithoutParams"));

		verifyNoMoreInteractions(listener);
	}

	@Test
	public void should_allow_for_re_running_a_parameterized_method_with_all_sets_when_other_test_methods_are_defined() throws Exception {
		// given
		Parameters runner = new Parameters(TestCaseWithAndWithoutParams.class);

		runner.filter(new KeepTestsWithDescriptionStartingWith("testWithParams"));

		// when
		runner.run(notifier);

		// then
		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams run1"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams run1"));

		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams run2"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams run2"));

		verifyNoMoreInteractions(listener);
	}

	@Test
	public void should_allow_for_re_running_a_parameterized_method_with_a_given_parameter_sets_when_other_test_methods_are_defined() throws Exception {
		// given
		Parameters runner = new Parameters(TestCaseWithAndWithoutParams.class);

		ParameterizedMethodRunner methodRunner = (ParameterizedMethodRunner) runner.getChildren().get(0);
		methodRunner.filter(new KeepTestsWithDescriptionStartingWith("testWithParams run2"));

		// when
		methodRunner.run(notifier);

		// then
		verify(listener).testStarted(anyDescriptionStartingWith("testWithParams run2"));
		verify(listener).testFinished(anyDescriptionStartingWith("testWithParams run2"));

		verifyNoMoreInteractions(listener);
	}

	private Description anyDescriptionStartingWith(final String expectedNameStart) {
		return argThat(new IsDescriptionStartingWith(expectedNameStart));
	}

	private Failure anyFailureWithDescriptionStartingWithAndMessage(final String expectedNameStart, String message) {
		return argThat(new IsFailureWithDescriptionStartingWithAndMessage(expectedNameStart, message));
	}

	private Failure anyFailureWithDescriptionStartingWithAndExpectedException(String expectedNameStart, Class<RuntimeException> expectedException) {
		return argThat(new IsFailureWithDescriptionStartingWithAndExpectedException(expectedNameStart, expectedException));
	}

	private static class IsDescriptionStartingWith extends ArgumentMatcher<Description> {
		private final String expectedNameStart;

		private IsDescriptionStartingWith(String expectedNameStart) {
			this.expectedNameStart = expectedNameStart;
		}

		@Override
		public void describeTo(org.hamcrest.Description d) {
			d.appendText("description starting with \"" + expectedNameStart + "\"");
		}

		@Override
		public boolean matches(Object argument) {
			return ((Description) argument).getDisplayName().startsWith(expectedNameStart);
		}
	}

	private static class IsFailureWithDescriptionStartingWithAndMessage extends ArgumentMatcher<Failure> {
		private final IsDescriptionStartingWith matcher;
		private final String expectedMessage;

		private IsFailureWithDescriptionStartingWithAndMessage(String expectedNameStart, String expectedMessage) {
			matcher = new IsDescriptionStartingWith(expectedNameStart);
			this.expectedMessage = expectedMessage;
		}

		@Override
		public void describeTo(org.hamcrest.Description d) {
			d.appendText("failure with ");
			d.appendDescriptionOf(matcher);
			d.appendText(" and message \"" + expectedMessage + "\"");
		}

		@Override
		public boolean matches(Object argument) {
			Failure failure = (Failure) argument;
			return matcher.matches(failure.getDescription()) && failure.getMessage().equals(expectedMessage);
		}
	}

	private static class IsFailureWithDescriptionStartingWithAndExpectedException extends ArgumentMatcher<Failure> {
		private final IsDescriptionStartingWith matcher;
		private final Class<RuntimeException> expectedException;

		private IsFailureWithDescriptionStartingWithAndExpectedException(String expectedNameStart, Class<RuntimeException> expectedException) {
			matcher = new IsDescriptionStartingWith(expectedNameStart);
			this.expectedException = expectedException;
		}

		@Override
		public void describeTo(org.hamcrest.Description d) {
			d.appendText("failure with ");
			d.appendDescriptionOf(matcher);
			d.appendText(" and expected exception \"" + expectedException + "\"");
		}

		@Override
		public boolean matches(Object argument) {
			Failure failure = (Failure) argument;
			return matcher.matches(failure.getDescription()) && failure.getException().getMessage().endsWith(expectedException.getName());
		}
	}

	private static class KeepTestsWithDescriptionStartingWith extends Filter {
		private final String descStart;

		public KeepTestsWithDescriptionStartingWith(String descStart) {
			this.descStart = descStart;
		}

		@Override
		public boolean shouldRun(Description description) {
			return description.getDisplayName().startsWith(descStart);
		}

		@Override
		public String describe() {
			return "test filter";
		}
	}
}
