// Fájlnév-konvenciók a CODING misszió fájlokhoz — ugyanaz a minta, mint a
// backend MissionService.isProtectedCadetFile()-jában és a
// gitea-templates/mission-*-template README-ekben: a Jest/pytest
// felismerési konvenciót használjuk, nem találunk ki új sémát.
export function isProtectedCadetFile(fileName: string): boolean {
  return /\.test\.(js|ts)$/.test(fileName) || /^test_.*\.py$/.test(fileName);
}

export function getMonacoLanguage(fileName: string): string {
  if (fileName.endsWith(".md")) return "markdown";
  if (fileName.endsWith(".py")) return "python";
  if (fileName.endsWith(".json")) return "json";
  if (fileName.endsWith(".ts")) return "typescript";
  if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) return "yaml";
  return "javascript";
}
