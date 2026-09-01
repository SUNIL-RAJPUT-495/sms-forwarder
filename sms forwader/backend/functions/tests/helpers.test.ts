import { sha256, generateSecureToken, generateDisplayCode, secureCompare, validateTimestamp, validatePayloadSize, MAX_TIMESTAMP_DRIFT_MS, MAX_PAYLOAD_BYTES } from "../src/util/helpers";

/**
 * Unit tests for backend utility functions.
 * These tests run with plain Jest (no Firebase emulator required).
 */

describe("sha256", () => {
  it("produces consistent hex output", () => {
    const hash = sha256("hello");
    expect(hash).toBe("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
  });

  it("different inputs produce different hashes", () => {
    expect(sha256("abc")).not.toBe(sha256("abd"));
  });
});

describe("generateSecureToken", () => {
  it("produces a base64url string", () => {
    const token = generateSecureToken(32);
    expect(token).toMatch(/^[A-Za-z0-9_-]+$/);
  });

  it("produces unique tokens", () => {
    const tokens = new Set(Array.from({ length: 100 }, () => generateSecureToken(32)));
    expect(tokens.size).toBe(100);
  });

  it("respects byte length parameter", () => {
    const token16 = generateSecureToken(16);
    const token32 = generateSecureToken(32);
    expect(token32.length).toBeGreaterThan(token16.length);
  });
});

describe("generateDisplayCode", () => {
  it("produces a 7-character code (3-hyphen-3 format)", () => {
    const code = generateDisplayCode("sometoken");
    expect(code).toMatch(/^[A-Z0-9]{3}-[A-Z0-9]{3}$/);
  });

  it("is deterministic for the same input", () => {
    const code1 = generateDisplayCode("test");
    const code2 = generateDisplayCode("test");
    expect(code1).toBe(code2);
  });

  it("produces different codes for different tokens", () => {
    expect(generateDisplayCode("token1")).not.toBe(generateDisplayCode("token2"));
  });
});

describe("secureCompare", () => {
  it("returns true for equal strings", () => {
    expect(secureCompare("hello", "hello")).toBe(true);
  });

  it("returns false for different strings", () => {
    expect(secureCompare("hello", "world")).toBe(false);
  });

  it("returns false for strings of different lengths", () => {
    expect(secureCompare("abc", "abcd")).toBe(false);
  });
});

describe("validateTimestamp", () => {
  it("accepts current timestamp", () => {
    expect(validateTimestamp(Date.now())).toBe(true);
  });

  it("accepts timestamp within allowed drift", () => {
    expect(validateTimestamp(Date.now() - (MAX_TIMESTAMP_DRIFT_MS - 1000))).toBe(true);
  });

  it("rejects timestamp outside drift window (too old)", () => {
    expect(validateTimestamp(Date.now() - (MAX_TIMESTAMP_DRIFT_MS + 1000))).toBe(false);
  });

  it("rejects future timestamp outside drift window", () => {
    expect(validateTimestamp(Date.now() + (MAX_TIMESTAMP_DRIFT_MS + 1000))).toBe(false);
  });
});

describe("validatePayloadSize", () => {
  const smallKey = Buffer.alloc(256).toString("base64");
  const smallNonce = Buffer.alloc(12).toString("base64");
  const smallBody = Buffer.alloc(200).toString("base64");

  it("accepts valid small payload", () => {
    expect(validatePayloadSize(smallKey, smallNonce, smallBody)).toBe(true);
  });

  it("rejects oversized payload", () => {
    const hugeBody = Buffer.alloc(MAX_PAYLOAD_BYTES + 1000).toString("base64");
    expect(validatePayloadSize(smallKey, smallNonce, hugeBody)).toBe(false);
  });
});
