/**
 * The shared skill directory used by profile selection. Directory entries are seeded through
 * Flyway migrations.
 *
 * SQL migrations seed the directory. Skill maps a row, SkillRepository supplies Spring Data
 * queries, and SkillController exposes public read access. Students select existing skills
 * through their profile service.
 */
package com.javac.campus.skill;
