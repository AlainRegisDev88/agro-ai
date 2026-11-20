"""Classification metrics in plain NumPy (shared by Keras and TFLite evaluation)."""

from __future__ import annotations

import numpy as np

UNKNOWN_SUFFIX = "__unknown"


def classification_report(y_true, y_pred, labels: list[str]) -> dict:
    k = len(labels)
    cm = np.zeros((k, k), dtype=np.int64)
    for t, p in zip(y_true, y_pred):
        cm[int(t), int(p)] += 1
    tp = np.diag(cm).astype(float)
    support = cm.sum(axis=1)
    predicted = cm.sum(axis=0)
    precision = np.divide(tp, predicted, out=np.zeros(k), where=predicted > 0)
    recall = np.divide(tp, support, out=np.zeros(k), where=support > 0)
    f1 = np.divide(2 * precision * recall, precision + recall, out=np.zeros(k), where=(precision + recall) > 0)
    present = support > 0
    total = int(cm.sum())
    report = {
        "n": total,
        "accuracy": float(tp.sum() / total) if total else 0.0,
        "macro_f1": float(f1[present].mean()) if present.any() else 0.0,
        "per_class": {
            labels[i]: {"precision": round(float(precision[i]), 4), "recall": round(float(recall[i]), 4),
                        "f1": round(float(f1[i]), 4), "support": int(support[i])}
            for i in range(k)
        },
        "confusion_matrix": cm.tolist(),
    }
    unknown = [i for i, l in enumerate(labels) if l.endswith(UNKNOWN_SUFFIX)]
    if unknown:
        # The unknown class is easy and would inflate "accuracy"; the headline number is
        # how often a real leaf gets its right condition (calling it "unknown" counts as wrong).
        known = [i for i in range(k) if i not in unknown]
        n_known = int(cm[known].sum())
        report["accuracy_known"] = float(tp[known].sum() / n_known) if n_known else 0.0
        n_unknown = int(cm[unknown].sum())
        report["unknown_recall"] = float(tp[unknown].sum() / n_unknown) if n_unknown else None
        # Real leaves wrongly rejected as "not my crop".
        report["known_called_unknown"] = float(cm[np.ix_(known, unknown)].sum() / n_known) if n_known else 0.0
    return report


def headline_accuracy(report: dict) -> float | None:
    """Accuracy on real leaves when the model has an unknown class, else plain accuracy."""
    return report.get("accuracy_known", report.get("accuracy"))
