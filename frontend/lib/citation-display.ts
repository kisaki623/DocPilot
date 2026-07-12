export interface CitationLocatorFields {
  sourceName?: string;
  documentTitle?: string;
  documentId?: number;
  sourceLocator?: string;
  pageNumber?: number;
  sectionPath?: string;
  blockType?: string;
  structureType?: string;
  chunkIndex?: number;
  indexVersion?: number;
  metadata?: Record<string, string | number | boolean | null | undefined>;
}

function metadataValue(
  item: CitationLocatorFields,
  key: string
): string | number | boolean | null | undefined {
  return item.metadata?.[key];
}

function firstText(
  item: CitationLocatorFields,
  ...keys: string[]
): string | undefined {
  for (const key of keys) {
    const value = metadataValue(item, key);
    if (typeof value === "string" && value.trim()) {
      return value.trim();
    }
  }
  return undefined;
}

function firstNumber(
  item: CitationLocatorFields,
  ...keys: string[]
): number | undefined {
  for (const key of keys) {
    const value = metadataValue(item, key);
    if (typeof value === "number" && Number.isFinite(value)) {
      return value;
    }
    if (typeof value === "string" && value.trim() && Number.isFinite(Number(value))) {
      return Number(value);
    }
  }
  return undefined;
}

export function citationSourceTitle(item: CitationLocatorFields): string {
  return (
    item.sourceName?.trim() ||
    item.documentTitle?.trim() ||
    firstText(item, "sourceName", "documentTitle", "fileName", "title") ||
    (item.documentId ? `文档 #${item.documentId}` : "来源文档")
  );
}

export function citationLocatorLabel(item: CitationLocatorFields): string {
  const sourceLocator = item.sourceLocator?.trim() || firstText(item, "sourceLocator", "locator");
  if (sourceLocator) {
    return sourceLocator;
  }

  const pageNumber = item.pageNumber ?? firstNumber(item, "pageNumber", "page");
  if (typeof pageNumber === "number" && Number.isFinite(pageNumber)) {
    return `第 ${pageNumber} 页`;
  }

  const sectionPath = item.sectionPath?.trim() || firstText(item, "sectionPath", "section");
  if (sectionPath) {
    return sectionPath;
  }

  const blockType = item.blockType?.trim() || firstText(item, "blockType");
  if (blockType) {
    return blockType;
  }

  const structureType = item.structureType?.trim() || firstText(item, "structureType");
  if (structureType) {
    return structureType;
  }

  return "";
}

export function citationStructureLabel(item: CitationLocatorFields): string {
  const parts = [
    item.sectionPath?.trim() || firstText(item, "sectionPath", "section"),
    item.blockType?.trim() || firstText(item, "blockType"),
    item.structureType?.trim() || firstText(item, "structureType")
  ].filter((part): part is string => Boolean(part));
  return Array.from(new Set(parts)).join(" / ");
}

export function citationChunkLabel(item: CitationLocatorFields): string {
  const parts: string[] = [];
  if (typeof item.chunkIndex === "number") {
    parts.push(`chunk #${item.chunkIndex}`);
  }
  if (typeof item.indexVersion === "number") {
    parts.push(`version ${item.indexVersion}`);
  }
  return parts.join(" · ");
}
