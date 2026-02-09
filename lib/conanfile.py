from conan import ConanFile
from conan.tools.cmake import cmake_layout


class ThirdpartyLibConan(ConanFile):
    settings = "os", "compiler", "build_type", "arch"
    generators = ["CMakeDeps", "CMakeToolchain"]
    options = {"shared": [True, False]}
    default_options = {"shared": True}
    requires = "zlib/1.3.1", "openssl/3.6.1", "libcurl/8.1.2", "nlohmann_json/3.11.3", "spdlog/1.15.1", "fmt/11.1.3"

    def configure(self):
        self.options["spdlog"].header_only = True
        self.options["fmt"].header_only = True
        self.options["zlib"].shared = True
        self.options["openssl"].shared = True
        self.options["libcurl"].shared = True

    def layout(self):
        cmake_layout(self)
        self.folders.build = f"build/{self.settings.arch}/{self.settings.build_type}"
        self.folders.generators = f"build/{self.settings.arch}/{self.settings.build_type}/generators"
        self.cpp.source.includedirs = ["src"]
