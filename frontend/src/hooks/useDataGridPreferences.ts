import { useCallback, useState } from "react";
import type {
  GridFilterModel,
  GridPaginationModel,
  GridSortModel,
} from "@mui/x-data-grid";
import { getCookie, setCookie } from "../utils/cookies";

const COOKIE_PREFIX = "legymernok_datagrid_";
const COOKIE_TTL_DAYS = 365;

interface DataGridPreferences {
  paginationModel: GridPaginationModel;
  filterModel: GridFilterModel;
  sortModel: GridSortModel;
}

const DEFAULT_FILTER_MODEL: GridFilterModel = { items: [] };
const DEFAULT_SORT_MODEL: GridSortModel = [];

function readStoredPreferences(
  cookieName: string,
): Partial<DataGridPreferences> | null {
  const raw = getCookie(cookieName);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Partial<DataGridPreferences>;
  } catch {
    return null;
  }
}

/**
 * Egy admin táblázat (MUI DataGrid) szűrő-, rendezés- és oldalméret-
 * beállításait helyi cookie-ba menti táblánként (a `tableKey` egyedi
 * kulcsa alapján), és visszatöltéskor automatikusan alkalmazza — a
 * felhasználónak ne kelljen minden oldal-megnyitáskor újra beállítania.
 *
 * A visszaadott props-ok közvetlenül a `<DataGrid>`-re köthetők
 * (`paginationModel`/`onPaginationModelChange` stb. — kontrollált mód,
 * NEM `initialState`).
 */
export function useDataGridPreferences(
  tableKey: string,
  defaultPageSize: number = 10,
  defaultSortModel: GridSortModel = DEFAULT_SORT_MODEL,
) {
  const cookieName = `${COOKIE_PREFIX}${tableKey}`;

  const [preferences, setPreferences] = useState<DataGridPreferences>(() => {
    const stored = readStoredPreferences(cookieName);
    return {
      paginationModel:
        stored?.paginationModel ?? { page: 0, pageSize: defaultPageSize },
      filterModel: stored?.filterModel ?? DEFAULT_FILTER_MODEL,
      sortModel: stored?.sortModel ?? defaultSortModel,
    };
  });

  const persist = useCallback(
    (patch: Partial<DataGridPreferences>) => {
      setPreferences((prev) => {
        const next = { ...prev, ...patch };
        setCookie(cookieName, JSON.stringify(next), COOKIE_TTL_DAYS);
        return next;
      });
    },
    [cookieName],
  );

  return {
    paginationModel: preferences.paginationModel,
    filterModel: preferences.filterModel,
    sortModel: preferences.sortModel,
    onPaginationModelChange: (model: GridPaginationModel) =>
      persist({ paginationModel: model }),
    onFilterModelChange: (model: GridFilterModel) =>
      persist({ filterModel: model }),
    onSortModelChange: (model: GridSortModel) => persist({ sortModel: model }),
  };
}
