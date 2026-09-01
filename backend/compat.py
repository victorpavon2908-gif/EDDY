"""Keep existing deployments configured while new deployments use NIKO_* names."""
import os


def env(name, default=""):
    value = os.getenv(name)
    if value is not None:
        return value
    return os.getenv(name.replace("NIKO_", "EDDY_", 1), default)
