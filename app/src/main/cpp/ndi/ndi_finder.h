#pragma once
#include <string>
#include <vector>

namespace ndicam {

// Runs an NDI find on this device for up to timeout_ms and returns the visible source names.
// Local sources are included, which lets the app verify that its own sender is advertised.
std::vector<std::string> find_sources(int timeout_ms, const char* extra_ips);

}  // namespace ndicam
