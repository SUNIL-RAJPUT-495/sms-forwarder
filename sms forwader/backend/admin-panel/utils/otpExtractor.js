/**
 * Utility function to extract 4-8 digit OTP from SMS content
 */
export function extractOTP(text) {
  if (!text) return null;
  const otpPatterns = [
    /(?:otp|code|pin|verificati|is|paswd|password)[\s:]*([0-9]{4,8})/i,
    /([0-9]{4,8})[\s:]*(?:is your|is the|to verify|for your)/i,
    /\b([0-9]{4,8})\b/
  ];
  for (const pattern of otpPatterns) {
    const match = text.match(pattern);
    if (match && match[1]) {
      return match[1];
    }
  }
  return null;
}

export default { extractOTP };

