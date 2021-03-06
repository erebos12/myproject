package util.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import thread.ThreadManager;
import util.logging.Logger;

public class SimpleServer implements Runnable {

    final private ThreadManager threadMgr = new ThreadManager();
    final private int port;
    private ServerSocket serverSocket = null;
    private String handlerClassName = null;
    private boolean isActive = false;
    final private String classname = SimpleServer.class.getName();
    private boolean serverStopped = false;
    private Thread ownThread = null;   
    public enum Status {
        READY_FOR_REQUEST, 
        PROCEED_INCOMING_REQUEST, 
        PROCEED_INCOMING_REQUEST_DONE,
        EXITING_SERVER_LOOP,
        INACTIVE;
    }
    private Status status = Status.INACTIVE;

    /**
     * Plain class that uses a server socket and pass the data received by the
     * socket to the handlerClassName (extended by abstract class
     * ClientSocketThread). Every new client socket created by server socket
     * (with method accept()) will run in an own thread. SimpleServer includes a
     * ThreadManager that manages GenericLoopingThreads which will be used for
     * new client sockets. See TestSimpleServer for reference implementation.
     * 
     * @param port
     *            Binding port for server socket
     * @param threadPoolSize
     *            Number of threads in the thread pool the server is using
     * @param handlerClassName
     *            Class name that handles the input data received by a socket
     * @throws IllegalArgumentException
     *             in case one of the params of constructor is invalid or
     *             missing
     */
    public SimpleServer (int port, int threadPoolSize, String handlerClassName) throws IllegalArgumentException
    {
        logMessage(Logger.LogLevel.INFO, "", "Constructing...");
        if (port <= 0)
        {
            throw new IllegalArgumentException("Error: Invalid port: " + port);
        }
        if (threadPoolSize <= 0)
        {
            throw new IllegalArgumentException(
                                               "Error: Invalid threadPoolSize: "
                                                   + threadPoolSize);
        }

        if (handlerClassName == null || handlerClassName.isEmpty())
        {
            throw new IllegalArgumentException(
                                               "Error: Invalid handlerClassName: "
                                                   + handlerClassName);
        }

        this.port = port;
        this.handlerClassName = handlerClassName;
        ownThread = new Thread(this);
        try
        {
            threadMgr.constructGenericThreadPool(threadPoolSize,
                                                 Thread.currentThread());
        }
        catch (Exception e)
        {
            logMessage(Logger.LogLevel.ERROR,
                       "SimpleServer",
                       "Error while constructing ThreadManager: "
                           + e.toString());
        }       
    }
    
	public void start() 
	{
		ownThread.start();
	}

    private void logMessage (final Logger.LogLevel logLevel, final String methodName, final String msg)
    {
    	 Logger.ctx.log(classname, methodName, logLevel, msg);
    }

    /**
     * Use to stop SimpleServer.
     * @throws Exception 
     */
    public void stop () throws IOException
    {
        String mn = "stop()";
        logMessage(Logger.LogLevel.INFO, mn, "stopping SimpleServer now...");         
        this.closeSocket();
        this.serverStopped = true;
        threadMgr.shutdownAllThreads(); 
        logMessage(Logger.LogLevel.INFO, mn, "SimpleServer stopped successfully.");  
    }

    public boolean getIsActive ()
    {
        int retry = 0;
        while (retry < 20)
        {
            try
            {
                if (this.isActive)
                {
                    break;
                }
                Thread.sleep(250);
                retry += 1;
            }
            catch (InterruptedException e)
            {
            }
        }
        return isActive;
    }
    
    public boolean isStopped ()
    {
        int retry = 0;
        while (retry < 20)
        {
            try
            {
                if (this.isActive == false)
                {
                    return true;
                }
                Thread.sleep(250);
                retry += 1;
            }
            catch (InterruptedException e)
            {
            }
        }
        return false;
    }

    public int getPort ()
    {
        return port;
    }
    
    public Status getStatus ()
    {
        return status;
    }
    
    private void setStatus (Status newStatus)
    {
        status = newStatus;
    }

    private void closeSocket () throws IOException
    {
		if (serverSocket != null) {
			serverSocket.close();
		}
    }
    
    @Override
    public void run ()
    {
        String MN = "run()";
        Socket clientSocket = null;
        try
        {
            serverSocket = new ServerSocket(port);
            isActive = true;
            while (!serverStopped)
            {
                logMessage(Logger.LogLevel.INFO, MN,
                           "listening on port " + serverSocket.getLocalPort());
                setStatus(Status.READY_FOR_REQUEST);
                clientSocket = serverSocket.accept();
                setStatus(Status.PROCEED_INCOMING_REQUEST);
                String remoteIp = clientSocket.getInetAddress().getHostAddress();
                int remotePort = clientSocket.getPort();
                logMessage(Logger.LogLevel.INFO, MN, "Connection established from "
                    + remoteIp + ":" + remotePort);
                try
                {
                    ClientSocketThread c = (ClientSocketThread) Class.forName(handlerClassName).newInstance();
                    if (c != null)
                    {
                        logMessage(Logger.LogLevel.INFO, MN,
                                   "Start running thread for " + handlerClassName);
                        c.setSocket(clientSocket);
                        threadMgr.runTask(c);    
                        setStatus(Status.PROCEED_INCOMING_REQUEST_DONE);
                    }
                }
                catch (IllegalAccessException | InstantiationException | ClassNotFoundException e)
                {
                    logMessage(Logger.LogLevel.ERROR, MN, e.toString());
                    try {
						throw e;
					} catch (ReflectiveOperationException e1) {
						// TODO Auto-generated catch block
						logMessage(Logger.LogLevel.ERROR, MN, e1.toString()); 
					}
                }     
            }          
        }      
        catch (IOException e)
        {
            logMessage(Logger.LogLevel.ERROR, MN, "socket closed");    
        }       
        finally
        {
            setStatus(Status.INACTIVE);   
            isActive = false;
        }
        logMessage(Logger.LogLevel.INFO, MN, "Shutting down SimpleServer.");
    }
}
