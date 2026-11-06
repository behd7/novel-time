/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** MinIO 公共读地址，默认 http://127.0.0.1:9000/novel */
  readonly VITE_ASSET_BASE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
