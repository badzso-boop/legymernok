export const createMockJwt = (roles: string | string[]) => {
  const roleArray = Array.isArray(roles) ? roles : [roles];
  const header = { alg: "HS256", typ: "JWT" };
  const payload = {
    sub: "cypress_admin",
    roles: roleArray,
    exp: Math.floor(Date.now() / 1000) + 3600,
    iat: Math.floor(Date.now() / 1000),
  };

  const stringifyAndEncode = (obj: any) => {
    return btoa(JSON.stringify(obj))
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/, "");
  };

  return `${stringifyAndEncode(header)}.${stringifyAndEncode(
    payload
  )}.mockSignature`;
};
