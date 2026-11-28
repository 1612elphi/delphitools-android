// usage: pencil-icons.mjs [pencil]
import { readFileSync, writeFileSync, mkdirSync, readdirSync, rmSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { homedir } from 'node:os';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const pencil = process.argv[2] ?? join(homedir(), 'GitRepos/dt-icon-pencil');
const svgs = join(pencil, 'assets/svg');
const out = join(root, 'app/src/main/res/drawable');
const header =
	'<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.00" stroke-linecap="round" stroke-linejoin="round">';

mkdirSync(out, { recursive: true });
for (const file of readdirSync(out)) {
	if (file.startsWith('pencil_')) rmSync(join(out, file));
}

const names = readdirSync(svgs)
	.filter((f) => f.endsWith('.svg'))
	.map((f) => f.slice(0, -4))
	.sort();
for (const name of names) {
	const svg = readFileSync(join(svgs, `${name}.svg`), 'utf8').trim();
	const lines = svg.split('\n').map((l) => l.trim());
	if (lines[0] !== header || lines.at(-1) !== '</svg>') {
		throw new Error(`${name}: unexpected svg header`);
	}
	const paths = lines.slice(1, -1).map((line) => {
		const m = /^<path d="([^"]+)" \/>$/.exec(line);
		if (!m) throw new Error(`${name}: unexpected element ${line}`);
		return m[1];
	});
	const body = paths
		.map(
			(d) =>
				`\t<path\n\t\tandroid:pathData="${d}"\n\t\tandroid:strokeColor="#FF000000"\n\t\tandroid:strokeWidth="2"\n\t\tandroid:strokeLineCap="round"\n\t\tandroid:strokeLineJoin="round" />`,
		)
		.join('\n');
	writeFileSync(
		join(out, `pencil_${name.replaceAll('-', '_')}.xml`),
		`<vector xmlns:android="http://schemas.android.com/apk/res/android"\n\tandroid:width="24dp"\n\tandroid:height="24dp"\n\tandroid:viewportWidth="24"\n\tandroid:viewportHeight="24">\n${body}\n</vector>\n`,
	);
}
console.log(`${names.length} drawables`);
