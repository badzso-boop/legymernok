import { add } from "./solution"; // Modern ES Module import

describe("add function", () => {
  test("should correctly add two positive numbers", () => {
    expect(add(2, 3)).toBe(5);
  });

  test("should correctly add two negative numbers", () => {
    expect(add(-5, -2)).toBe(-7);
  });

  test("should correctly add zero to a number", () => {
    expect(add(0, 7)).toBe(7);
  });

  test("should handle floating-point numbers", () => {
    expect(add(0.1, 0.2)).toBeCloseTo(0.3); // Jest-specifikus: lebegőpontos számokhoz
  });
});
