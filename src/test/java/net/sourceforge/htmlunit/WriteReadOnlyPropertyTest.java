package net.sourceforge.htmlunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.ScriptableObject;

/**
 * Test that read-only properties can be... set when needed.
 * This was the standard behavior in Rhino until 1.7R2 but has changed then.
 * It is needed to simulate IE as well as FF2 (but not FF3).
 * @see <a href="https://bugzilla.mozilla.org/show_bug.cgi?id=519933">Rhino bug 519933</a>
 * @author Marc Guillemot
 * @author Jake Cobb
 */
public class WriteReadOnlyPropertyTest {

	/**
	 * @throws Exception if the test fails
	 */
	@Test
	public void testWriteReadOnly_accepted() throws Exception {
		testWriteReadOnly(true);
	}

	/**
	 * @throws Exception if the test fails
	 */
	@Test
	public void testWriteReadOnly_throws() throws Exception {
		try {
			testWriteReadOnly(false);
			fail();
		}
		catch (EcmaError e) {
			assertTrue(e.getMessage(), e.getMessage().contains("Cannot set property [Foo].myProp that has only a getter"));
		}
	}

	void testWriteReadOnly(final boolean acceptWriteReadOnly) throws Exception {
		final Method readMethod = Foo.class.getMethod("getMyProp");
		final Foo foo = new Foo("hello");
        foo.defineProperty("myProp", null, readMethod, null, ScriptableObject.EMPTY);

		final String script = "foo.myProp = 123; foo.myProp";

		final ContextAction action = new ContextAction() {
            @Override
			public Object run(final Context cx) {

				final ScriptableObject top = cx.initStandardObjects();
				ScriptableObject.putProperty(top, "foo", foo);
				
				System.out.println(cx.evaluateString(top, script, "script", 0, null));
				return null;
			}
		};
		
		final ContextFactory contextFactory = makeContextFactory(acceptWriteReadOnly);
		contextFactory.call(action);
	}
	
	ContextFactory makeContextFactory(final boolean acceptWriteReadOnly) {
		return new ContextFactory() {
			@Override
			protected boolean hasFeature(final Context cx, final int featureIndex) {
				if (Context.FEATURE_HTMLUNIT_ASK_OBJECT_TO_WRITE_READONLY == featureIndex) {
					return acceptWriteReadOnly;
				}
				return super.hasFeature(cx, featureIndex);
			}
		};
	}
	
	/** @see https://sourceforge.net/p/htmlunit/bugs/1633/ */
	@Test
	public void testWriteReadOnlyNoCorruption() throws Exception {
		final String script = ""
			+ "var proto = Object.create(Object.prototype, \n"
			+ "    {myProp: {get: function() { return 'hello'; }}\n"
			+ "});\n"
			+ "var o1 = Object.create(proto), o2 = Object.create(proto);\n"
			+ "o2.myProp = 'bar'; result = o1.myProp;";

		final ContextAction action = new ContextAction() {
            @Override
			public Object run(final Context cx) {
				final ScriptableObject top = cx.initStandardObjects();
				Object result = cx.evaluateString(top, script, "script", 0, null);
				assertEquals("Prototype was corrupted", "hello", result);
				return null;
			}
		};

		final ContextFactory contextFactory = makeContextFactory(true);
		contextFactory.call(action);
	}

	/**
	 * Simple utility allowing to better see the concerned scope while debugging
	 */
	static class Foo extends ScriptableObject {
		final String prop_;
		Foo(final String label) {
			prop_ = label;
		}

		@Override
		public String getClassName() {
			return "Foo";
		}
		
		public String getMyProp() {
			return prop_;
		}
	};
}
