package com.example.upnpsocketapp;

import org.teleal.cling.binding.annotations.UpnpAction;
import org.teleal.cling.binding.annotations.UpnpInputArgument;
import org.teleal.cling.binding.annotations.UpnpService;
import org.teleal.cling.binding.annotations.UpnpServiceId;
import org.teleal.cling.binding.annotations.UpnpServiceType;
import org.teleal.cling.binding.annotations.UpnpStateVariable;

@UpnpService(serviceId=@UpnpServiceId("ShareService"), serviceType=@UpnpServiceType(value="ShareService", version=1))
public class ShareService
{

  @UpnpStateVariable(defaultValue="0")
  private boolean status = false;

  @UpnpStateVariable(defaultValue="0", sendEvents=false)
  private boolean target = false;

  @UpnpAction(out={@org.teleal.cling.binding.annotations.UpnpOutputArgument(name="ResultStatus")})
  public boolean getStatus()
  {
    return this.status;
  }

  @UpnpAction(out={@org.teleal.cling.binding.annotations.UpnpOutputArgument(name="RetTargetValue")})
  public boolean getTarget()
  {
    return this.target;
  }

  @UpnpAction
  public void setTarget(@UpnpInputArgument(name="NewTargetValue") boolean paramBoolean)
  {
    this.target = paramBoolean;
    this.status = paramBoolean;
    System.out.println("Switch is: " + this.status);
  }
}

/* Location:           G:\classes-dex2jar.jar
 * Qualified Name:     com.matt.clingtest.ShareService
 * JD-Core Version:    0.6.2
 */