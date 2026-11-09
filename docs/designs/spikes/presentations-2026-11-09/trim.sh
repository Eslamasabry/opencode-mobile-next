#!/bin/sh
# trim.sh <pack-dir>: remove what the Node build never loads
cd "$1/node_modules" || exit 1
rm -rf @types
rm -rf pptxgenjs/types pptxgenjs/README.md pptxgenjs/dist/*.map pptxgenjs/dist/pptxgen.bundle.js pptxgenjs/dist/pptxgen.es.js pptxgenjs/dist/pptxgen.min.js
cd pdfkit && rm -rf .yarn .github tools types CHANGELOG.md README.md rollup.config.mjs vitest.config.js jsconfig.json
cd js && rm -f *.map pdfkit.browser* pdfkit.standalone.js pdfkit.old.js pdfkit.node.mjs output.mjs
cd ../..
find . -name "*.map" -delete; find . -name "*.d.ts" -delete; find . -name "*.md" -delete; find . -name "*.mjs" -path "*bidi-js*" -delete
find pdfkit/js/standard-fonts -name "*.mjs" -delete
rm -rf fontkit/src @swc/helpers/esm @swc/helpers/_
