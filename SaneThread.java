/**
 * @author David Barts
 * @version 0.1
 * @since 2016-01-22
 *
 * Because standard Java threads are wonky when it comes to exceptions.
 */
abstract class SaneThread extends Thread {
    /**
     * Run the thread.
     */
    public void run() {
    	try {
    		runn();
    	} catch (Exception exc) {
    		System.err.format("Thread %d (%s) aborted due to exception...%n",
    			getId(), getClass().getSimpleName());
    		exc.printStackTrace();
    	}
    }

    abstract void runn() throws Exception;
}
