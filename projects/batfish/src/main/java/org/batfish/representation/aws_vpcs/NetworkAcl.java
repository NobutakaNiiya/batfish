package org.batfish.representation.aws_vpcs;

import com.google.common.collect.ImmutableSortedSet;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.batfish.common.BatfishLogger;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.IpProtocol;
import org.batfish.datamodel.IpWildcard;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.SubRange;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class NetworkAcl implements AwsVpcEntity, Serializable {

  private static final long serialVersionUID = 1L;

  private List<NetworkAclEntry> _entries = new LinkedList<>();

  private List<NetworkAclAssociation> _networkAclAssociations = new LinkedList<>();

  private String _networkAclId;

  private String _vpcId;

  public NetworkAcl(JSONObject jObj, BatfishLogger logger) throws JSONException {
    _networkAclId = jObj.getString(JSON_KEY_NETWORK_ACL_ID);
    _vpcId = jObj.getString(JSON_KEY_VPC_ID);

    JSONArray associations = jObj.getJSONArray(JSON_KEY_ASSOCIATIONS);
    initAssociations(associations, logger);

    JSONArray entries = jObj.getJSONArray(JSON_KEY_ENTRIES);
    initEntries(entries, logger);
  }

  private IpAccessList getAcl(boolean isEgress) {
    String listName = _networkAclId + (isEgress ? "_egress" : "_ingress");
    Map<Integer, IpAccessListLine> lineMap = new TreeMap<>();
    for (NetworkAclEntry entry : _entries) {
      if ((isEgress && entry.getIsEgress()) || (!isEgress && !entry.getIsEgress())) {
        IpAccessListLine line = new IpAccessListLine();
        int key = entry.getRuleNumber();
        LineAction action = entry.getIsAllow() ? LineAction.ACCEPT : LineAction.REJECT;
        line.setAction(action);
        Prefix prefix = entry.getCidrBlock();
        if (!prefix.equals(Prefix.ZERO)) {
          if (isEgress) {
            line.setDstIps(ImmutableSortedSet.of(new IpWildcard(prefix)));
          } else {
            line.setSrcIps(ImmutableSortedSet.of(new IpWildcard(prefix)));
          }
        }
        IpProtocol protocol = IpPermissions.toIpProtocol(entry.getProtocol());
        String protocolStr = protocol != null ? protocol.toString() : "ALL";
        if (protocol != null) {
          line.setIpProtocols(ImmutableSortedSet.of(protocol));
        }
        int fromPort = entry.getFromPort();
        int toPort = entry.getToPort();
        SubRange portRange = new SubRange(fromPort, toPort);
        if (fromPort != -1 || toPort != -1) {
          if (fromPort == -1) {
            fromPort = 0;
          }
          if (toPort == -1) {
            toPort = 65535;
          }
          line.setDstPorts(ImmutableSortedSet.of(portRange));
        }
        String portStr;
        if (protocol == IpProtocol.ICMP) {
          // TODO: flesh these out
          portStr = "some ICMP type(s)/code(s)";
        } else if ((fromPort == 0 && toPort == 65535) || (fromPort == -1 && toPort == -1)) {
          portStr = "ALL";
        } else {
          portStr = portRange.toString();
        }
        String actionStr = action == LineAction.ACCEPT ? "ALLOW" : "DENY";
        String lineNumber = key == 32767 ? "*" : Integer.toString(key);
        line.setName(
            String.format("%s %s %s %s %s", lineNumber, protocolStr, portStr, prefix, actionStr));
        lineMap.put(key, line);
      }
    }
    List<IpAccessListLine> lines = new ArrayList<>(lineMap.values());
    IpAccessList list = new IpAccessList(listName, lines);
    return list;
  }

  public List<NetworkAclAssociation> getAssociations() {
    return _networkAclAssociations;
  }

  public IpAccessList getEgressAcl() {
    return getAcl(true);
  }

  @Override
  public String getId() {
    return _networkAclId;
  }

  public IpAccessList getIngressAcl() {
    return getAcl(false);
  }

  public String getVpcId() {
    return _vpcId;
  }

  private void initAssociations(JSONArray associations, BatfishLogger logger) throws JSONException {
    for (int index = 0; index < associations.length(); index++) {
      JSONObject childObject = associations.getJSONObject(index);
      _networkAclAssociations.add(new NetworkAclAssociation(childObject, logger));
    }
  }

  private void initEntries(JSONArray entries, BatfishLogger logger) throws JSONException {
    for (int index = 0; index < entries.length(); index++) {
      JSONObject childObject = entries.getJSONObject(index);
      _entries.add(new NetworkAclEntry(childObject, logger));
    }
  }
}
