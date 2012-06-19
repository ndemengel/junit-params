package ndemengel;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

public class Parameters extends ParentRunner<Object> {

	private final List<Object> children;

	public Parameters(Class<?> testClass) throws InitializationError {
		super(testClass);
		children = createChildren();
	}

	private List<Object> createChildren() throws InitializationError {
		final TestClass testClass = getTestClass();

		List<Object> runners = new ArrayList<Object>();
		for (final FrameworkMethod m : testClass.getAnnotatedMethods(Test.class)) {
			WithParameters withParams = m.getAnnotation(WithParameters.class);
			if (withParams == null) {
				runners.add(new JUnit4MethodRunner(testClass.getJavaClass(), m));
			}
			else {
				Object[][] parameters = getParameters(withParams.value(), testClass);
				runners.add(new ParameterizedMethodRunner(m, testClass.getJavaClass(), withParams.value(), parameters));
			}
		}
		return runners;
	}

	@Override
	protected List<Object> getChildren() {
		return children;
	}

	@Override
	protected Description describeChild(Object child) {
		if (child instanceof JUnit4MethodRunner) {
			return ((JUnit4MethodRunner) child).getDescription();
		}

		return ((ParameterizedMethodRunner) child).getDescription();
	}

	@Override
	protected void runChild(Object child, RunNotifier notifier) {
		if (child instanceof JUnit4MethodRunner) {
			((JUnit4MethodRunner) child).runChild(notifier);
		}
		else {
			((ParameterizedMethodRunner) child).run(notifier);
		}
	}

	private Object[][] getParameters(String paramField, TestClass testClass) throws InitializationError {
		for (Field f : testClass.getJavaClass().getDeclaredFields()) {
			if (!f.getName().equals(paramField)) {
				continue;
			}
			if (!Modifier.isStatic(f.getModifiers())) {
				throw new InitializationError("Field \"" + f.getName() + "\" must be static");
			}
			if (!f.isAccessible()) {
				try {
					f.setAccessible(true);
				} catch (SecurityException e) {
					throw new IllegalArgumentException("Could not access field: " + f.getName() + ". Please make it public.", e);
				}
			}
			try {
				return (Object[][]) f.get(null);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			} catch (ClassCastException e) {
				throw new InitializationError("Parameter fields must be arrays of non primitive types (for now)");
			}
		}

		throw new InitializationError("Field \"" + paramField + "\" not found");
	}

	@Override
	protected void collectInitializationErrors(List<Throwable> errors) {
		super.collectInitializationErrors(errors);

		for (FrameworkMethod m : getTestClass().getAnnotatedMethods(Test.class)) {
			if (m.getAnnotation(WithParameters.class) == null) {
				m.validatePublicVoidNoArg(false, errors);
			}
			else {
				m.validatePublicVoid(false, errors);
			}
		}
	}

	static class ParameterizedMethodRunner extends JUnit4ClassRunner {

		private final FrameworkMethod testMethod;
		private final String paramField;
		private final Object[][] allParams;

		public ParameterizedMethodRunner(FrameworkMethod testMethod, Class<?> testClass, String paramField, Object[][] allParams) throws InitializationError {
			super(testClass);
			this.testMethod = testMethod;
			this.paramField = paramField;
			this.allParams = allParams;
			validateParams();
		}

		@Override
		protected void collectInitializationErrors(List<Throwable> errors) {
			// does not call parent validation: class has already been validated by ParamsRunner
		}

		private void validateParams() throws InitializationError {
			List<Throwable> errors = new ArrayList<Throwable>();
			if (allParams.length == 0) {
				errors.add(new Exception("Field \"" + paramField + "\" was expected to define parameters"));
			}

			Class<?>[] expectedParamTypes = testMethod.getMethod().getParameterTypes();
			int expectedParamCount = expectedParamTypes.length;

			if (expectedParamCount == 0) {
				errors.add(new Exception("Method \"" + testMethod.getName() + "\" takes no arguments"));
			}

			for (Object[] params : allParams) {
				int actualParamCount = params.length;
				if (expectedParamCount != actualParamCount) {
					errors.add(new Exception("Method \"" + testMethod.getName() + "\" requires " //
							+ expectedParamCount + (expectedParamCount == 1 ? " parameter" : " parameters") //
							+ ", but " + actualParamCount + (actualParamCount == 1 ? " is" : " are") + " defined"));
				}
				else {
					for (int i = 0; i < expectedParamCount; i++) {
						Class<?> expectedType = expectedParamTypes[i];
						Object param = params[i];
						if (param != null && !expectedType.isInstance(param)) {
							errors.add(new Exception("Parameter <" + param + "> should be a <" + expectedType + ">"));
						}
					}
				}
			}

			if (!errors.isEmpty()) {
				throw new InitializationError(errors);
			}
		}

		@Override
		protected String getName() {
			return testMethod.getName();
		}

		@Override
		protected List<FrameworkMethod> getChildren() {
			ArrayList<FrameworkMethod> children = new ArrayList<FrameworkMethod>();
			for (Object[] params : allParams) {
				children.add(new FrameworkMethodWithParams(getTestClass().getJavaClass(), testMethod, params));
			}
			return children;
		}

		@Override
		protected Description describeChild(FrameworkMethod child) {
			return ((FrameworkMethodWithParams) child).getDescription();
		}

		@Override
		public Description getDescription() {
			Description description = Description.createSuiteDescription(getName(), testMethod.getAnnotations());
			for (Description child : super.getDescription().getChildren()) {
				description.addChild(child);
			}
			return description;
		}

		@Override
		protected String testName(FrameworkMethod method) {
			return method.getName();
		}

		@Override
		protected Statement methodInvoker(FrameworkMethod method, Object target) {
			return ((FrameworkMethodWithParams) method).getInvoker(target);
		}
	}

	private static class FrameworkMethodWithParams extends FrameworkMethod {

		private final Class<?> testClass;
		private final FrameworkMethod testMethod;
		private final Object[] params;
		private final String name;

		public FrameworkMethodWithParams(Class<?> testClass, FrameworkMethod testMethod, Object[] params) {
			super(testMethod.getMethod());
			this.testClass = testClass;
			this.testMethod = testMethod;
			this.params = params;
			this.name = buildName(testMethod, params);
		}

		private static String buildName(FrameworkMethod m, Object[] params) {
			StringBuilder sb = new StringBuilder(m.getName()).append(" ");
			for (int i = 0; i < params.length; i++) {
				if (i != 0) {
					sb.append(", ");
				}
				sb.append(params[i]);
			}
			return sb.toString();
		}

		public Description getDescription() {
			return Description.createTestDescription(testClass, name, testMethod.getAnnotations());
		}

		public Statement getInvoker(Object target) {
			return new InvokeMethodWithParams(testMethod, target, params);
		}
	}

	private static class InvokeMethodWithParams extends Statement {
		private final FrameworkMethod testMethod;
		private final Object target;
		private final Object[] params;

		public InvokeMethodWithParams(FrameworkMethod testMethod, Object target, Object[] params) {
			this.testMethod = testMethod;
			this.target = target;
			this.params = params;
		}

		@Override
		public void evaluate() throws Throwable {
			testMethod.invokeExplosively(target, params);
		}
	}

	private static class JUnit4MethodRunner {

		private final JUnit4ClassRunner runner;
		private final FrameworkMethod testMethod;
		private final Class<?> testClass;

		public JUnit4MethodRunner(Class<?> klass, FrameworkMethod testMethod) throws InitializationError {
			this.testClass = klass;
			runner = new JUnit4ClassRunner(klass);
			this.testMethod = testMethod;
		}

		public Description getDescription() {
			return Description.createTestDescription(testClass, testMethod.getName(), testMethod.getAnnotations());

		}

		public void runChild(RunNotifier notifier) {
			runner.runChild(testMethod, notifier);
		}
	}

	private static class JUnit4ClassRunner extends BlockJUnit4ClassRunner {

		public JUnit4ClassRunner(Class<?> klass) throws InitializationError {
			super(klass);
		}

		@Override
		protected void validateTestMethods(List<Throwable> errors) {
			// methods already validated, and they must support parameters
		}

		// makes it visible to JUnit4MethodRunner
		@Override
		protected void runChild(FrameworkMethod method, RunNotifier notifier) {
			super.runChild(method, notifier);
		}
	}
}