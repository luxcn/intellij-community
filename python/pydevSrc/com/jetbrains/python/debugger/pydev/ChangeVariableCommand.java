package com.jetbrains.python.debugger.pydev;


import com.jetbrains.python.debugger.IPyDebugProcess;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;

public class ChangeVariableCommand extends AbstractFrameCommand {

  private final String myVariableName;
  private final String myValue;
  private PyDebugValue myNewValue = null;
  private final IPyDebugProcess myDebugProcess;

  public ChangeVariableCommand(final RemoteDebugger debugger, final String threadId, final String frameId, final String variableName,
                               final String value) {
    super(debugger, CHANGE_VARIABLE, threadId, frameId);
    myVariableName = variableName;
    myValue = value;
    myDebugProcess = debugger.getDebugProcess();
  }

  public String getPayload() {
    return new StringBuilder().append(myThreadId).append('\t').append(myFrameId).append('\t').append("FRAME\t").append(myVariableName)
      .append('\t').append(ProtocolParser.encodeExpression(myValue)).toString();
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  protected void processResponse(final ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    final PyDebugValue value = ProtocolParser.parseValue(response.getPayload());
    myNewValue = new PyDebugValue(myVariableName, value.getType(), value.getValue(), value.isContainer(), null, myDebugProcess);
  }

  public PyDebugValue getNewValue() {
    return myNewValue;
  }
}
