package com.slayertaskluck.ui;

import java.util.List;

/** What the panel can ask the plugin to do. Called on the Swing thread. */
public interface PanelActions
{
	void setWanted(String group, List<String> tasks);

	void setExcluded(String rollId, boolean excluded);

	/** Re-score past rolls at a master with the current wanted tasks. */
	void rescore(String group);

	void resetData();

	void exportCsv();
}
