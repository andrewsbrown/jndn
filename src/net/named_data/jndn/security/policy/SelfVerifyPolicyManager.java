/**
 * Copyright (C) 2014 Regents of the University of California.
 * @author: Jeff Thompson <jefft0@remap.ucla.edu>
 * @author: From code in ndn-cxx by Yingdi Yu <yingdi@cs.ucla.edu>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * A copy of the GNU Lesser General Public License is in the file COPYING.
 */

package net.named_data.jndn.security.policy;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;
import net.named_data.jndn.KeyLocatorType;
import net.named_data.jndn.Name;
import net.named_data.jndn.Sha256WithRsaSignature;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.encoding.WireFormat;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.OnVerified;
import net.named_data.jndn.security.OnVerifiedInterest;
import net.named_data.jndn.security.OnVerifyFailed;
import net.named_data.jndn.security.OnVerifyInterestFailed;
import net.named_data.jndn.security.ValidationRequest;
import net.named_data.jndn.security.certificate.IdentityCertificate;
import net.named_data.jndn.security.identity.IdentityStorage;
import net.named_data.jndn.util.Blob;
import net.named_data.jndn.util.SignedBlob;

/**
 * A SelfVerifyPolicyManager implements a PolicyManager to use the public key
 * DER in the data packet's KeyLocator (if available) or look in the
 * IdentityStorage for the public key with the name in the KeyLocator (if
 * available) and use it to verify the data packet, without searching a
 * certificate chain.  If the public key can't be found, the verification fails.
 */
public class SelfVerifyPolicyManager extends PolicyManager {
  /**
   * Create a new SelfVerifyPolicyManager which will look up the public key in
   * the given identityStorage.
   * @param identityStorage The IdentityStorage for looking up the
   * public key.  This points to an object which must remain valid during the
   * life of this SelfVerifyPolicyManager.
   */
  public SelfVerifyPolicyManager(IdentityStorage identityStorage)
  {
    identityStorage_ = identityStorage;
  }

  /**
   * Create a new SelfVerifyPolicyManager which will look up the public key in
   * the given identityStorage.
   * Since there is no IdentotyStorage, don't look for a public key with the
   * name in the KeyLocator and rely on the KeyLocator having the full public
   * key DER.
   */
  public SelfVerifyPolicyManager()
  {
    identityStorage_ = null;
  }

  /**
   * Never skip verification.
   * @param data The received data packet.
   * @return false.
   */
  public boolean skipVerifyAndTrust(Data data)
  {
    return false;
  }

  /**
   * Never skip verification.
   * @param interest The received interest.
   * @return false.
   */
  public boolean skipVerifyAndTrust(Interest interest)
  {
    return false;
  }

  /**
   * Always return true to use the self-verification rule for the received data.
   * @param data The received data packet.
   * @return true.
   */
  public boolean requireVerify(Data data)
  {
    return true;
  }

  /**
   * Always return true to use the self-verification rule for the received interest.
   * @param interest The received interest.
   * @return true.
   */
  public boolean requireVerify(Interest interest)
  {
    return true;
  }

  /**
   * Use the public key DER in the data packet's KeyLocator (if available) or
   * look in the IdentityStorage for the public key with the name in the
   * KeyLocator (if available) and use it to verify the data packet.  If the
   * public key can't be found, call onVerifyFailed.
   * @param data The Data object with the signature to check.
   * @param stepCount The number of verification steps that have been done, used
   * to track the verification progress. (stepCount is ignored.)
   * @param onVerified If the signature is verified, this calls onVerified(data).
   * @param onVerifyFailed If the signature check fails or can't find the public
   * key, this calls onVerifyFailed(data).
   * @return null for no further step for looking up a certificate chain.
   */
  public ValidationRequest checkVerificationPolicy
    (Data data, int stepCount, OnVerified onVerified,
     OnVerifyFailed onVerifyFailed) throws SecurityException
  {
    // wireEncode returns the cached encoding if available.
    if (verify(data.getSignature(), data.wireEncode()))
      onVerified.onVerified(data);
    else
      onVerifyFailed.onVerifyFailed(data);

    // No more steps, so return a null ValidationRequest.
    return null;
  }

  /**
   * Use wireFormat.decodeSignatureInfoAndValue to decode the last two name
   * components of the signed interest. Use the public key DER in the signed
   * interest SignatureInfo's KeyLocator (if available) or look in the
   * IdentityStorage for the public key with the name in the KeyLocator
   * (if available) and use it to verify the interest. If the public key can't
   * be found, call onVerifyFailed.
   * @param interest The interest with the signature to check.
   * @param stepCount The number of verification steps that have been done, used
   * to track the verification progress. (stepCount is ignored.)
   * @param onVerified If the signature is verified, this calls
   * onVerified.onVerifiedInterest(interest).
   * @param onVerifyFailed If the signature check fails or can't find the public
   * key, this calls onVerifyFailed.onVerifyInterestFailed(interest).
   * @return null for no further step for looking up a certificate chain.
   */
  public ValidationRequest
  checkVerificationPolicy
    (Interest interest, int stepCount, OnVerifiedInterest onVerified,
     OnVerifyInterestFailed onVerifyFailed, WireFormat wireFormat) 
    throws net.named_data.jndn.security.SecurityException
  {
    // Decode the last two name components of the signed interest
    net.named_data.jndn.Signature signature;
    try {
      signature = wireFormat.decodeSignatureInfoAndValue
        (interest.getName().get(-2).getValue().buf(),
         interest.getName().get(-1).getValue().buf());
    }
    catch (EncodingException ex) {
      Logger.getLogger(SelfVerifyPolicyManager.class.getName()).log
        (Level.INFO, "Cannot decode the signed interest SignatureInfo and value", ex);
      onVerifyFailed.onVerifyInterestFailed(interest);
      return null;
    }

    // wireEncode returns the cached encoding if available.
    if (verify(signature, interest.wireEncode(wireFormat)))
      onVerified.onVerifiedInterest(interest);
    else
      onVerifyFailed.onVerifyInterestFailed(interest);

    // No more steps, so return a null ValidationRequest.
    return null;
  }

  /**
   * Override to always indicate that the signing certificate name and data name
   * satisfy the signing policy.
   * @param dataName The name of data to be signed.
   * @param certificateName The name of signing certificate.
   * @return true to indicate that the signing certificate can be used to sign
   * the data.
   */
  public boolean checkSigningPolicy(Name dataName, Name certificateName)
  {
    return true;
  }

  /**
   * Override to indicate that the signing identity cannot be inferred.
   * @param dataName The name of data to be signed.
   * @return An empty name because cannot infer.
   */
  public Name inferSigningIdentity(Name dataName)
  {
    return new Name();
  }

  /**
   * Check the type of signatureInfo to get the KeyLocator. Use the public key
   * DER in the KeyLocator (if available) or look in the IdentityStorage for the
   * public key with the name in the KeyLocator (if available) and use it to
   * verify the signedBlob. If the public key can't be found, return false.
   * (This is a generalized method which can verify both a Data packet and an
   * interest.)
   * @param signatureInfo An object of a subclass of Signature, e.g.
   * Sha256WithRsaSignature.
   * @param signedBlob the SignedBlob with the signed portion to verify.
   * @return True if the signature is verified, false if failed.
   */
  private boolean
  verify(net.named_data.jndn.Signature signatureInfo, SignedBlob signedBlob) throws net.named_data.jndn.security.SecurityException
  {
    if (!(signatureInfo instanceof Sha256WithRsaSignature))
      throw new SecurityException("SelfVerifyPolicyManager: Signature is not Sha256WithRsaSignature.");
    Sha256WithRsaSignature signature =
      (Sha256WithRsaSignature)signatureInfo;

    if (signature.getKeyLocator().getType() == KeyLocatorType.KEY) {
      // Use the public key DER directly.
      // wireEncode returns the cached encoding if available.
      if (verifySha256WithRsaSignature
          (signature, signedBlob, signature.getKeyLocator().getKeyData()))
        return true;
      else
        return false;
    }
    else if (signature.getKeyLocator().getType() == KeyLocatorType.KEYNAME &&
             identityStorage_ != null) {
      // Assume the key name is a certificate name.
      Blob publicKeyDer = identityStorage_.getKey
        (IdentityCertificate.certificateNameToPublicKeyName
         (signature.getKeyLocator().getKeyName()));
      if (publicKeyDer.isNull())
        // Can't find the public key with the name.
        return false;

      if (verifySha256WithRsaSignature
          (signature, signedBlob, publicKeyDer))
        return true;
      else
        return false;
    }
    else
      // Can't find a key to verify.
      return false;
  }

  private final IdentityStorage identityStorage_;
}
