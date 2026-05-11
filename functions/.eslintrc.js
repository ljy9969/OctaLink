module.exports = {
  root: true,
  env: { es2020: true, node: true },
  extends: [
    "eslint:recommended",
    "google",
    "plugin:@typescript-eslint/recommended",
  ],
  parser: "@typescript-eslint/parser",
  parserOptions: { project: ["tsconfig.json"], sourceType: "module" },
  ignorePatterns: ["/lib/**/*", "/generated/**/*"],
  plugins: ["@typescript-eslint", "import"],
  rules: {
    quotes: ["error", "double"],
    "import/no-unresolved": 0,
    "indent": ["error", 2],
    "object-curly-spacing": ["error", "always"],
    "require-jsdoc": "off",
    "valid-jsdoc": "off",
    // Windows checkout 은 CRLF, *nix 는 LF — git core.autocrlf 영향. Cloud Functions 는 어차피
    // Linux 컨테이너에서 실행되므로 lint 단에서 줄바꿈 강제 불필요.
    "linebreak-style": "off",
  },
};
