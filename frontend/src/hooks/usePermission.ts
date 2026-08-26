import { useAuth } from "../context/AuthContext";

/**
 * Egyetlen permission meglétét ellenőrzi a bejelentkezett felhasználón.
 *
 * A JWT `roles` tömbje a szerepkör-nevek MELLETT a flattened permissionöket is tartalmazza
 * (ld. `AuthContext.hasRole` kommentje), tehát a `hasRole("mission:create")` technikailag
 * már ma is helyes eredményt ad — ez a hook csak a szándékot teszi olvashatóvá a hívási
 * helyeken: a `hasRole` név egy permission-ellenőrzésnél félrevezető.
 *
 * @example
 * const canCreateMissions = usePermission("mission:create");
 */
export const usePermission = (permission: string): boolean => {
  const { hasRole } = useAuth();
  return hasRole(permission);
};
