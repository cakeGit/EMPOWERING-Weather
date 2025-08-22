const fs = require("fs");
const path = require("path");

// Source and destination
const repoRoot = path.resolve(__dirname, "..");
const candidates = [
    path.join(repoRoot, "web"),
    path.join(repoRoot, "src", "web"),
];
const src = candidates.find((p) => fs.existsSync(p));
if (!src) {
    console.error('No "web" directory found. Checked:', candidates.join(", "));
    process.exit(2);
}

const dest = path.join(
    repoRoot,
    "android",
    "app",
    "src",
    "main",
    "assets",
    "public"
);

function rimrafSync(p) {
    if (!fs.existsSync(p)) return;
    const stat = fs.lstatSync(p);
    if (stat.isDirectory() && !stat.isSymbolicLink()) {
        for (const entry of fs.readdirSync(p)) {
            rimrafSync(path.join(p, entry));
        }
        fs.rmdirSync(p);
    } else {
        fs.unlinkSync(p);
    }
}

function copyRecursive(srcPath, destPath) {
    const stat = fs.lstatSync(srcPath);
    if (stat.isDirectory()) {
        if (!fs.existsSync(destPath))
            fs.mkdirSync(destPath, { recursive: true });
        for (const entry of fs.readdirSync(srcPath)) {
            copyRecursive(
                path.join(srcPath, entry),
                path.join(destPath, entry)
            );
        }
    } else if (stat.isSymbolicLink()) {
        const link = fs.readlinkSync(srcPath);
        try {
            fs.symlinkSync(link, destPath);
        } catch (e) {
            // On Windows creating symlinks may fail without privileges; fall back to copying the target file contents
            const targetPath = path.resolve(path.dirname(srcPath), link);
            fs.copyFileSync(targetPath, destPath);
        }
    } else {
        fs.copyFileSync(srcPath, destPath);
    }
}

try {
    console.log(`Source: ${src}`);
    console.log(`Destination: ${dest}`);

    // Remove destination if exists
    if (fs.existsSync(dest)) {
        console.log("Removing existing destination...");
        rimrafSync(dest);
    }

    console.log("Copying files...");
    copyRecursive(src, dest);
    console.log("Done.");
} catch (err) {
    console.error("Error copying web assets:", err);
    process.exit(1);
}
